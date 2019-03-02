 package org.apache.lucene.index;
 
 /**
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements.  See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License.  You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
 
 import org.apache.lucene.analysis.Analyzer;
 import org.apache.lucene.document.Document;
 import org.apache.lucene.search.Similarity;
 import org.apache.lucene.store.Directory;
 import org.apache.lucene.store.FSDirectory;
 import org.apache.lucene.store.IndexInput;
 import org.apache.lucene.store.IndexOutput;
 import org.apache.lucene.store.Lock;
 import org.apache.lucene.store.RAMDirectory;
 
 import java.io.File;
 import java.io.IOException;
 import java.io.PrintStream;
 import java.util.Vector;
 import java.util.HashSet;
 
 /**
   An IndexWriter creates and maintains an index.
 
   <p>The third argument (<code>create</code>) to the 
   <a href="#IndexWriter(org.apache.lucene.store.Directory, org.apache.lucene.analysis.Analyzer, boolean)"><b>constructor</b></a>
   determines whether a new index is created, or whether an existing index is
   opened for the addition of new documents.  Note that you
   can open an index with create=true even while readers are
   using the index.  The old readers will continue to search
   the "point in time" snapshot they had opened, and won't
   see the newly created index until they re-open.</p>
 
   <p>In either case, documents are added with the <a
   href="#addDocument(org.apache.lucene.document.Document)"><b>addDocument</b></a> method.  
   When finished adding documents, <a href="#close()"><b>close</b></a> should be called.</p>
 
   <p>If an index will not have more documents added for a while and optimal search
   performance is desired, then the <a href="#optimize()"><b>optimize</b></a>
   method should be called before the index is closed.</p>
   
   <p>Opening an IndexWriter creates a lock file for the directory in use. Trying to open
   another IndexWriter on the same directory will lead to an IOException. The IOException
   is also thrown if an IndexReader on the same directory is used to delete documents
   from the index.</p>
   
   @see IndexModifier IndexModifier supports the important methods of IndexWriter plus deletion
   */
 
 public class IndexWriter {
 
   /**
    * Default value for the write lock timeout (1,000).
    * @see #setDefaultWriteLockTimeout
    */
   public static long WRITE_LOCK_TIMEOUT = 1000;
 
   private long writeLockTimeout = WRITE_LOCK_TIMEOUT;
 
   public static final String WRITE_LOCK_NAME = "write.lock";
 
   /**
    * Default value is 10. Change using {@link #setMergeFactor(int)}.
    */
   public final static int DEFAULT_MERGE_FACTOR = 10;
 
   /**
    * Default value is 10. Change using {@link #setMaxBufferedDocs(int)}.
    */
   public final static int DEFAULT_MAX_BUFFERED_DOCS = 10;
 
   /**
    * Default value is {@link Integer#MAX_VALUE}. Change using {@link #setMaxMergeDocs(int)}.
    */
   public final static int DEFAULT_MAX_MERGE_DOCS = Integer.MAX_VALUE;
 
   /**
    * Default value is 10,000. Change using {@link #setMaxFieldLength(int)}.
    */
   public final static int DEFAULT_MAX_FIELD_LENGTH = 10000;
 
   /**
    * Default value is 128. Change using {@link #setTermIndexInterval(int)}.
    */
   public final static int DEFAULT_TERM_INDEX_INTERVAL = 128;
   
   private Directory directory;  // where this index resides
   private Analyzer analyzer;    // how to analyze text
 
   private Similarity similarity = Similarity.getDefault(); // how to normalize
 
   private boolean inTransaction = false; // true iff we are in a transaction
   private boolean commitPending; // true if segmentInfos has changes not yet committed
   private HashSet protectedSegments; // segment names that should not be deleted until commit
   private SegmentInfos rollbackSegmentInfos;      // segmentInfos we will fallback to if the commit fails
 
  private SegmentInfos segmentInfos = new SegmentInfos();       // the segments
  private SegmentInfos ramSegmentInfos = new SegmentInfos();    // the segments in ramDirectory
   private final RAMDirectory ramDirectory = new RAMDirectory(); // for temp segs
   private IndexFileDeleter deleter;
 
   private Lock writeLock;
 
   private int termIndexInterval = DEFAULT_TERM_INDEX_INTERVAL;
 
   /** Use compound file setting. Defaults to true, minimizing the number of
    * files used.  Setting this to false may improve indexing performance, but
    * may also cause file handle problems.
    */
   private boolean useCompoundFile = true;
 
   private boolean closeDir;
 
   /** Get the current setting of whether to use the compound file format.
    *  Note that this just returns the value you set with setUseCompoundFile(boolean)
    *  or the default. You cannot use this to query the status of an existing index.
    *  @see #setUseCompoundFile(boolean)
    */
   public boolean getUseCompoundFile() {
     return useCompoundFile;
   }
 
   /** Setting to turn on usage of a compound file. When on, multiple files
    *  for each segment are merged into a single file once the segment creation
    *  is finished. This is done regardless of what directory is in use.
    */
   public void setUseCompoundFile(boolean value) {
     useCompoundFile = value;
   }
 
   /** Expert: Set the Similarity implementation used by this IndexWriter.
    *
    * @see Similarity#setDefault(Similarity)
    */
   public void setSimilarity(Similarity similarity) {
     this.similarity = similarity;
   }
 
   /** Expert: Return the Similarity implementation used by this IndexWriter.
    *
    * <p>This defaults to the current value of {@link Similarity#getDefault()}.
    */
   public Similarity getSimilarity() {
     return this.similarity;
   }
 
   /** Expert: Set the interval between indexed terms.  Large values cause less
    * memory to be used by IndexReader, but slow random-access to terms.  Small
    * values cause more memory to be used by an IndexReader, and speed
    * random-access to terms.
    *
    * This parameter determines the amount of computation required per query
    * term, regardless of the number of documents that contain that term.  In
    * particular, it is the maximum number of other terms that must be
    * scanned before a term is located and its frequency and position information
    * may be processed.  In a large index with user-entered query terms, query
    * processing time is likely to be dominated not by term lookup but rather
    * by the processing of frequency and positional data.  In a small index
    * or when many uncommon query terms are generated (e.g., by wildcard
    * queries) term lookup may become a dominant cost.
    *
    * In particular, <code>numUniqueTerms/interval</code> terms are read into
    * memory by an IndexReader, and, on average, <code>interval/2</code> terms
    * must be scanned for each random term access.
    *
    * @see #DEFAULT_TERM_INDEX_INTERVAL
    */
   public void setTermIndexInterval(int interval) {
     this.termIndexInterval = interval;
   }
 
   /** Expert: Return the interval between indexed terms.
    *
    * @see #setTermIndexInterval(int)
    */
   public int getTermIndexInterval() { return termIndexInterval; }
 
   /**
    * Constructs an IndexWriter for the index in <code>path</code>.
    * Text will be analyzed with <code>a</code>.  If <code>create</code>
    * is true, then a new, empty index will be created in
    * <code>path</code>, replacing the index already there, if any.
    *
    * @param path the path to the index directory
    * @param a the analyzer to use
    * @param create <code>true</code> to create the index or overwrite
    *  the existing one; <code>false</code> to append to the existing
    *  index
    * @throws IOException if the directory cannot be read/written to, or
    *  if it does not exist, and <code>create</code> is
    *  <code>false</code>
    */
   public IndexWriter(String path, Analyzer a, boolean create)
        throws IOException {
     init(path, a, create);
   }
 
   /**
    * Constructs an IndexWriter for the index in <code>path</code>.
    * Text will be analyzed with <code>a</code>.  If <code>create</code>
    * is true, then a new, empty index will be created in
    * <code>path</code>, replacing the index already there, if any.
    *
    * @param path the path to the index directory
    * @param a the analyzer to use
    * @param create <code>true</code> to create the index or overwrite
    *  the existing one; <code>false</code> to append to the existing
    *  index
    * @throws IOException if the directory cannot be read/written to, or
    *  if it does not exist, and <code>create</code> is
    *  <code>false</code>
    */
   public IndexWriter(File path, Analyzer a, boolean create)
        throws IOException {
     init(path, a, create);
   }
 
   /**
    * Constructs an IndexWriter for the index in <code>d</code>.
    * Text will be analyzed with <code>a</code>.  If <code>create</code>
    * is true, then a new, empty index will be created in
    * <code>d</code>, replacing the index already there, if any.
    *
    * @param d the index directory
    * @param a the analyzer to use
    * @param create <code>true</code> to create the index or overwrite
    *  the existing one; <code>false</code> to append to the existing
    *  index
    * @throws IOException if the directory cannot be read/written to, or
    *  if it does not exist, and <code>create</code> is
    *  <code>false</code>
    */
   public IndexWriter(Directory d, Analyzer a, boolean create)
        throws IOException {
     init(d, a, create, false);
   }
 
   /**
    * Constructs an IndexWriter for the index in
    * <code>path</code>, creating it first if it does not
    * already exist, otherwise appending to the existing
    * index.  Text will be analyzed with <code>a</code>.
    *
    * @param path the path to the index directory
    * @param a the analyzer to use
    * @throws IOException if the directory cannot be
    *  created or read/written to
    */
   public IndexWriter(String path, Analyzer a) 
     throws IOException {
     if (IndexReader.indexExists(path)) {
       init(path, a, false);
     } else {
       init(path, a, true);
     }
   }
 
   /**
    * Constructs an IndexWriter for the index in
    * <code>path</code>, creating it first if it does not
    * already exist, otherwise appending to the existing
    * index.  Text will be analyzed with
    * <code>a</code>.
    *
    * @param path the path to the index directory
    * @param a the analyzer to use
    * @throws IOException if the directory cannot be
    *  created or read/written to
    */
   public IndexWriter(File path, Analyzer a) 
     throws IOException {
     if (IndexReader.indexExists(path)) {
       init(path, a, false);
     } else {
       init(path, a, true);
     }
   }
 
   /**
    * Constructs an IndexWriter for the index in
    * <code>d</code>, creating it first if it does not
    * already exist, otherwise appending to the existing
    * index.  Text will be analyzed with <code>a</code>.
    *
    * @param d the index directory
    * @param a the analyzer to use
    * @throws IOException if the directory cannot be
    *  created or read/written to
    */
   public IndexWriter(Directory d, Analyzer a) 
     throws IOException {
     if (IndexReader.indexExists(d)) {
       init(d, a, false, false);
     } else {
       init(d, a, true, false);
     }
   }
 
   private IndexWriter(Directory d, Analyzer a, final boolean create, boolean closeDir)
     throws IOException {
     init(d, a, create, closeDir);
   }
 
   private void init(String path, Analyzer a, final boolean create)
     throws IOException {
     init(FSDirectory.getDirectory(path), a, create, true);
   }
 
   private void init(File path, Analyzer a, final boolean create)
     throws IOException {
     init(FSDirectory.getDirectory(path), a, create, true);
   }
 
   private void init(Directory d, Analyzer a, final boolean create, boolean closeDir)
     throws IOException {
     this.closeDir = closeDir;
     directory = d;
     analyzer = a;
 
     if (create) {
       // Clear the write lock in case it's leftover:
       directory.clearLock(IndexWriter.WRITE_LOCK_NAME);
     }
 
     Lock writeLock = directory.makeLock(IndexWriter.WRITE_LOCK_NAME);
     if (!writeLock.obtain(writeLockTimeout)) // obtain write lock
       throw new IOException("Index locked for write: " + writeLock);
     this.writeLock = writeLock;                   // save it
 
     try {
       if (create) {
         // Try to read first.  This is to allow create
         // against an index that's currently open for
         // searching.  In this case we write the next
         // segments_N file with no segments:
         try {
           segmentInfos.read(directory);
           segmentInfos.clear();
         } catch (IOException e) {
           // Likely this means it's a fresh directory
         }
         segmentInfos.write(directory);
       } else {
         segmentInfos.read(directory);
       }
 
       // Create a deleter to keep track of which files can
       // be deleted:
       deleter = new IndexFileDeleter(segmentInfos, directory);
       deleter.setInfoStream(infoStream);
       deleter.findDeletableFiles();
       deleter.deleteFiles();
 
     } catch (IOException e) {
       this.writeLock.release();
       this.writeLock = null;
       throw e;
     }
   }
 
   /** Determines the largest number of documents ever merged by addDocument().
    * Small values (e.g., less than 10,000) are best for interactive indexing,
    * as this limits the length of pauses while indexing to a few seconds.
    * Larger values are best for batched indexing and speedier searches.
    *
    * <p>The default value is {@link Integer#MAX_VALUE}.
    */
   public void setMaxMergeDocs(int maxMergeDocs) {
     this.maxMergeDocs = maxMergeDocs;
   }
 
   /**
    * @see #setMaxMergeDocs
    */
   public int getMaxMergeDocs() {
     return maxMergeDocs;
   }
 
   /**
    * The maximum number of terms that will be indexed for a single field in a
    * document.  This limits the amount of memory required for indexing, so that
    * collections with very large files will not crash the indexing process by
    * running out of memory.<p/>
    * Note that this effectively truncates large documents, excluding from the
    * index terms that occur further in the document.  If you know your source
    * documents are large, be sure to set this value high enough to accomodate
    * the expected size.  If you set it to Integer.MAX_VALUE, then the only limit
    * is your memory, but you should anticipate an OutOfMemoryError.<p/>
    * By default, no more than 10,000 terms will be indexed for a field.
    */
   public void setMaxFieldLength(int maxFieldLength) {
     this.maxFieldLength = maxFieldLength;
   }
 
   /**
    * @see #setMaxFieldLength
    */
   public int getMaxFieldLength() {
     return maxFieldLength;
   }
 
   /** Determines the minimal number of documents required before the buffered
    * in-memory documents are merged and a new Segment is created.
    * Since Documents are merged in a {@link org.apache.lucene.store.RAMDirectory},
    * large value gives faster indexing.  At the same time, mergeFactor limits
    * the number of files open in a FSDirectory.
    *
    * <p> The default value is 10.
    *
    * @throws IllegalArgumentException if maxBufferedDocs is smaller than 2
    */
   public void setMaxBufferedDocs(int maxBufferedDocs) {
     if (maxBufferedDocs < 2)
       throw new IllegalArgumentException("maxBufferedDocs must at least be 2");
     this.minMergeDocs = maxBufferedDocs;
   }
 
   /**
    * @see #setMaxBufferedDocs
    */
   public int getMaxBufferedDocs() {
     return minMergeDocs;
   }
 
   /** Determines how often segment indices are merged by addDocument().  With
    * smaller values, less RAM is used while indexing, and searches on
    * unoptimized indices are faster, but indexing speed is slower.  With larger
    * values, more RAM is used during indexing, and while searches on unoptimized
    * indices are slower, indexing is faster.  Thus larger values (> 10) are best
    * for batch index creation, and smaller values (< 10) for indices that are
    * interactively maintained.
    *
    * <p>This must never be less than 2.  The default value is 10.
    */
   public void setMergeFactor(int mergeFactor) {
     if (mergeFactor < 2)
       throw new IllegalArgumentException("mergeFactor cannot be less than 2");
     this.mergeFactor = mergeFactor;
   }
 
   /**
    * @see #setMergeFactor
    */
   public int getMergeFactor() {
     return mergeFactor;
   }
 
   /** If non-null, information about merges and a message when
    * maxFieldLength is reached will be printed to this.
    */
   public void setInfoStream(PrintStream infoStream) {
     this.infoStream = infoStream;
   }
 
   /**
    * @see #setInfoStream
    */
   public PrintStream getInfoStream() {
     return infoStream;
   }
 
   /**
    * Sets the maximum time to wait for a write lock (in milliseconds) for this instance of IndexWriter.  @see
    * @see #setDefaultWriteLockTimeout to change the default value for all instances of IndexWriter.
    */
   public void setWriteLockTimeout(long writeLockTimeout) {
     this.writeLockTimeout = writeLockTimeout;
   }
 
   /**
    * @see #setWriteLockTimeout
    */
   public long getWriteLockTimeout() {
     return writeLockTimeout;
   }
 
   /**
    * Sets the default (for any instance of IndexWriter) maximum time to wait for a write lock (in
    * milliseconds).
    */
   public static void setDefaultWriteLockTimeout(long writeLockTimeout) {
     IndexWriter.WRITE_LOCK_TIMEOUT = writeLockTimeout;
   }
 
   /**
    * @see #setDefaultWriteLockTimeout
    */
   public static long getDefaultWriteLockTimeout() {
     return IndexWriter.WRITE_LOCK_TIMEOUT;
   }
 
   /**
    * Flushes all changes to an index and closes all
    * associated files.
    *
    * <p> If an Exception is hit during close, eg due to disk
    * full or some other reason, then both the on-disk index
    * and the internal state of the IndexWriter instance will
    * be consistent.  However, the close will not be complete
    * even though part of it (flushing buffered documents)
    * may have succeeded, so the write lock will still be
    * held.</p>
    * 
    * <p> If you can correct the underlying cause (eg free up
    * some disk space) then you can call close() again.
    * Failing that, if you want to force the write lock to be
    * released (dangerous, because you may then lose buffered
    * docs in the IndexWriter instance) then you can do
    * something like this:</p>
    *
    * <pre>
    * try {
    *   writer.close();
    * } finally {
    *   if (IndexReader.isLocked(directory)) {
    *     IndexReader.unlock(directory);
    *   }
    * }
    * </pre>
    *
    * after which, you must be certain not to use the writer
    * instance anymore.</p>
    */
   public synchronized void close() throws IOException {
     flushRamSegments();
     ramDirectory.close();
     if (writeLock != null) {
       writeLock.release();                          // release write lock
       writeLock = null;
     }
     if(closeDir)
       directory.close();
   }
 
   /** Release the write lock, if needed. */
   protected void finalize() throws Throwable {
     try {
       if (writeLock != null) {
         writeLock.release();                        // release write lock
         writeLock = null;
       }
     } finally {
       super.finalize();
     }
   }
 
   /** Returns the Directory used by this index. */
   public Directory getDirectory() {
       return directory;
   }
 
   /** Returns the analyzer used by this index. */
   public Analyzer getAnalyzer() {
       return analyzer;
   }
 
 
   /** Returns the number of documents currently in this index. */
   public synchronized int docCount() {
     int count = ramSegmentInfos.size();
     for (int i = 0; i < segmentInfos.size(); i++) {
       SegmentInfo si = segmentInfos.info(i);
       count += si.docCount;
     }
     return count;
   }
 
   /**
    * The maximum number of terms that will be indexed for a single field in a
    * document.  This limits the amount of memory required for indexing, so that
    * collections with very large files will not crash the indexing process by
    * running out of memory.<p/>
    * Note that this effectively truncates large documents, excluding from the
    * index terms that occur further in the document.  If you know your source
    * documents are large, be sure to set this value high enough to accomodate
    * the expected size.  If you set it to Integer.MAX_VALUE, then the only limit
    * is your memory, but you should anticipate an OutOfMemoryError.<p/>
    * By default, no more than 10,000 terms will be indexed for a field.
    *
    */
   private int maxFieldLength = DEFAULT_MAX_FIELD_LENGTH;
 
   /**
    * Adds a document to this index.  If the document contains more than
    * {@link #setMaxFieldLength(int)} terms for a given field, the remainder are
    * discarded.
    *
    * <p> Note that if an Exception is hit (for example disk full)
    * then the index will be consistent, but this document
    * may not have been added.  Furthermore, it's possible
    * the index will have one segment in non-compound format
    * even when using compound files (when a merge has
    * partially succeeded).</p>
    *
    * <p> This method periodically flushes pending documents
    * to the Directory (every {@link #setMaxBufferedDocs}),
    * and also periodically merges segments in the index
    * (every {@link #setMergeFactor} flushes).  When this
    * occurs, the method will take more time to run (possibly
    * a long time if the index is large), and will require
    * free temporary space in the Directory to do the
    * merging.</p>
    *
    * <p>The amount of free space required when a merge is
    * triggered is up to 1X the size of all segments being
    * merged, when no readers/searchers are open against the
    * index, and up to 2X the size of all segments being
    * merged when readers/searchers are open against the
    * index (see {@link #optimize()} for details).  Most
    * merges are small (merging the smallest segments
    * together), but whenever a full merge occurs (all
    * segments in the index, which is the worst case for
    * temporary space usage) then the maximum free disk space
    * required is the same as {@link #optimize}.</p>
    */
   public void addDocument(Document doc) throws IOException {
     addDocument(doc, analyzer);
   }
 
   /**
    * Adds a document to this index, using the provided analyzer instead of the
    * value of {@link #getAnalyzer()}.  If the document contains more than
    * {@link #setMaxFieldLength(int)} terms for a given field, the remainder are
    * discarded.
    *
    * <p>See {@link #addDocument(Document)} for details on
    * index and IndexWriter state after an Exception, and
    * flushing/merging temporary free space requirements.</p>
    */
   public void addDocument(Document doc, Analyzer analyzer) throws IOException {
    DocumentWriter dw =
      new DocumentWriter(ramDirectory, analyzer, this);
    dw.setInfoStream(infoStream);
    String segmentName = newRAMSegmentName();
    dw.addDocument(segmentName, doc);
     synchronized (this) {
      ramSegmentInfos.addElement(new SegmentInfo(segmentName, 1, ramDirectory, false, false));
       maybeFlushRamSegments();
     }
   }
 
   // for test purpose
   final synchronized int getRAMSegmentCount() {
     return ramSegmentInfos.size();
   }
 
  private final synchronized String newRAMSegmentName() {
     return "_ram_" + Integer.toString(ramSegmentInfos.counter++, Character.MAX_RADIX);
   }
 
   // for test purpose
   final synchronized int getSegmentCount(){
     return segmentInfos.size();
   }
 
   // for test purpose
   final synchronized int getDocCount(int i) {
     if (i >= 0 && i < segmentInfos.size()) {
       return segmentInfos.info(i).docCount;
     } else {
       return -1;
     }
   }
 
  private final synchronized String newSegmentName() {
     return "_" + Integer.toString(segmentInfos.counter++, Character.MAX_RADIX);
   }
 
   /** Determines how often segment indices are merged by addDocument().  With
    * smaller values, less RAM is used while indexing, and searches on
    * unoptimized indices are faster, but indexing speed is slower.  With larger
    * values, more RAM is used during indexing, and while searches on unoptimized
    * indices are slower, indexing is faster.  Thus larger values (> 10) are best
    * for batch index creation, and smaller values (< 10) for indices that are
    * interactively maintained.
    *
    * <p>This must never be less than 2.  The default value is {@link #DEFAULT_MERGE_FACTOR}.
 
    */
   private int mergeFactor = DEFAULT_MERGE_FACTOR;
 
   /** Determines the minimal number of documents required before the buffered
    * in-memory documents are merging and a new Segment is created.
    * Since Documents are merged in a {@link org.apache.lucene.store.RAMDirectory},
    * large value gives faster indexing.  At the same time, mergeFactor limits
    * the number of files open in a FSDirectory.
    *
    * <p> The default value is {@link #DEFAULT_MAX_BUFFERED_DOCS}.
 
    */
   private int minMergeDocs = DEFAULT_MAX_BUFFERED_DOCS;
 
 
   /** Determines the largest number of documents ever merged by addDocument().
    * Small values (e.g., less than 10,000) are best for interactive indexing,
    * as this limits the length of pauses while indexing to a few seconds.
    * Larger values are best for batched indexing and speedier searches.
    *
    * <p>The default value is {@link #DEFAULT_MAX_MERGE_DOCS}.
 
    */
   private int maxMergeDocs = DEFAULT_MAX_MERGE_DOCS;
 
   /** If non-null, information about merges will be printed to this.
 
    */
   private PrintStream infoStream = null;
 
   /** Merges all segments together into a single segment,
    * optimizing an index for search.
    * 
    * <p>Note that this requires substantial temporary free
    * space in the Directory (see <a target="_top"
    * href="http://issues.apache.org/jira/browse/LUCENE-764">LUCENE-764</a>
    * for details):</p>
    *
    * <ul>
    * <li>
    * 
    * <p>If no readers/searchers are open against the index,
    * then free space required is up to 1X the total size of
    * the starting index.  For example, if the starting
    * index is 10 GB, then you must have up to 10 GB of free
    * space before calling optimize.</p>
    *
    * <li>
    * 
    * <p>If readers/searchers are using the index, then free
    * space required is up to 2X the size of the starting
    * index.  This is because in addition to the 1X used by
    * optimize, the original 1X of the starting index is
    * still consuming space in the Directory as the readers
    * are holding the segments files open.  Even on Unix,
    * where it will appear as if the files are gone ("ls"
    * won't list them), they still consume storage due to
    * "delete on last close" semantics.</p>
    * 
    * <p>Furthermore, if some but not all readers re-open
    * while the optimize is underway, this will cause > 2X
    * temporary space to be consumed as those new readers
    * will then hold open the partially optimized segments at
    * that time.  It is best not to re-open readers while
    * optimize is running.</p>
    *
    * </ul>
    *
    * <p>The actual temporary usage could be much less than
    * these figures (it depends on many factors).</p>
    *
    * <p>Once the optimize completes, the total size of the
    * index will be less than the size of the starting index.
    * It could be quite a bit smaller (if there were many
    * pending deletes) or just slightly smaller.</p>
    *
    * <p>If an Exception is hit during optimize(), for example
    * due to disk full, the index will not be corrupt and no
    * documents will have been lost.  However, it may have
    * been partially optimized (some segments were merged but
    * not all), and it's possible that one of the segments in
    * the index will be in non-compound format even when
    * using compound file format.  This will occur when the
    * Exception is hit during conversion of the segment into
    * compound format.</p>
   */
   public synchronized void optimize() throws IOException {
     flushRamSegments();
     while (segmentInfos.size() > 1 ||
            (segmentInfos.size() == 1 &&
             (SegmentReader.hasDeletions(segmentInfos.info(0)) ||
              SegmentReader.hasSeparateNorms(segmentInfos.info(0)) ||
              segmentInfos.info(0).dir != directory ||
              (useCompoundFile &&
               (!SegmentReader.usesCompoundFile(segmentInfos.info(0))))))) {
       int minSegment = segmentInfos.size() - mergeFactor;
       mergeSegments(segmentInfos, minSegment < 0 ? 0 : minSegment, segmentInfos.size());
     }
   }
 
   /*
    * Begin a transaction.  During a transaction, any segment
    * merges that happen (or ram segments flushed) will not
    * write a new segments file and will not remove any files
    * that were present at the start of the transaction.  You
    * must make a matched (try/finall) call to
    * commitTransaction() or rollbackTransaction() to finish
    * the transaction.
    */
   private void startTransaction() throws IOException {
     if (inTransaction) {
       throw new IOException("transaction is already in process");
     }
     rollbackSegmentInfos = (SegmentInfos) segmentInfos.clone();
     protectedSegments = new HashSet();
     for(int i=0;i<segmentInfos.size();i++) {
       SegmentInfo si = (SegmentInfo) segmentInfos.elementAt(i);
       protectedSegments.add(si.name);
     }
     inTransaction = true;
   }
 
   /*
    * Rolls back the transaction and restores state to where
    * we were at the start.
    */
   private void rollbackTransaction() throws IOException {
 
     // Keep the same segmentInfos instance but replace all
     // of its SegmentInfo instances.  This is so the next
     // attempt to commit using this instance of IndexWriter
     // will always write to a new generation ("write once").
     segmentInfos.clear();
     segmentInfos.addAll(rollbackSegmentInfos);
 
     // Ask deleter to locate unreferenced files & remove
     // them:
     deleter.clearPendingFiles();
     deleter.findDeletableFiles();
     deleter.deleteFiles();
 
     clearTransaction();
   }
 
   /*
    * Commits the transaction.  This will write the new
    * segments file and remove and pending deletions we have
    * accumulated during the transaction
    */
   private void commitTransaction() throws IOException {
     if (commitPending) {
       boolean success = false;
       try {
         // If we hit eg disk full during this write we have
         // to rollback.:
         segmentInfos.write(directory);         // commit changes
         success = true;
       } finally {
         if (!success) {
           rollbackTransaction();
         }
       }
       deleter.commitPendingFiles();
       commitPending = false;
     }
 
     clearTransaction();
   }
 
   /* Should only be called by rollbackTransaction &
    * commitTransaction */
   private void clearTransaction() {
     protectedSegments = null;
     rollbackSegmentInfos = null;
     inTransaction = false;
   }
 
 
 
   /** Merges all segments from an array of indexes into this index.
    *
    * <p>This may be used to parallelize batch indexing.  A large document
    * collection can be broken into sub-collections.  Each sub-collection can be
    * indexed in parallel, on a different thread, process or machine.  The
    * complete index can then be created by merging sub-collection indexes
    * with this method.
    *
    * <p>After this completes, the index is optimized.
    *
    * <p>This method is transactional in how Exceptions are
    * handled: it does not commit a new segments_N file until
    * all indexes are added.  This means if an Exception
    * occurs (for example disk full), then either no indexes
    * will have been added or they all will have been.</p>
    *
    * <p>If an Exception is hit, it's still possible that all
    * indexes were successfully added.  This happens when the
    * Exception is hit when trying to build a CFS file.  In
    * this case, one segment in the index will be in non-CFS
    * format, even when using compound file format.</p>
    *
    * <p>Also note that on an Exception, the index may still
    * have been partially or fully optimized even though none
    * of the input indexes were added. </p>
    *
    * <p>Note that this requires temporary free space in the
    * Directory up to 2X the sum of all input indexes
    * (including the starting index).  If readers/searchers
    * are open against the starting index, then temporary
    * free space required will be higher by the size of the
    * starting index (see {@link #optimize()} for details).
    * </p>
    *
    * <p>Once this completes, the final size of the index
    * will be less than the sum of all input index sizes
    * (including the starting index).  It could be quite a
    * bit smaller (if there were many pending deletes) or
    * just slightly smaller.</p>
    *
    * <p>See <a target="_top"
    * href="http://issues.apache.org/jira/browse/LUCENE-702">LUCENE-702</a>
    * for details.</p>
    */
   public synchronized void addIndexes(Directory[] dirs)
     throws IOException {
 
     optimize();					  // start with zero or 1 seg
 
     int start = segmentInfos.size();
 
     boolean success = false;
 
     startTransaction();
 
     try {
       for (int i = 0; i < dirs.length; i++) {
         SegmentInfos sis = new SegmentInfos();	  // read infos from dir
         sis.read(dirs[i]);
         for (int j = 0; j < sis.size(); j++) {
           segmentInfos.addElement(sis.info(j));	  // add each info
         }
       }
 
       // merge newly added segments in log(n) passes
       while (segmentInfos.size() > start+mergeFactor) {
         for (int base = start; base < segmentInfos.size(); base++) {
           int end = Math.min(segmentInfos.size(), base+mergeFactor);
           if (end-base > 1) {
             mergeSegments(segmentInfos, base, end);
           }
         }
       }
       success = true;
     } finally {
       if (success) {
         commitTransaction();
       } else {
         rollbackTransaction();
       }
     }
 
     optimize();					  // final cleanup
   }
 
   /**
    * Merges all segments from an array of indexes into this index.
    * <p>
    * This is similar to addIndexes(Directory[]). However, no optimize()
    * is called either at the beginning or at the end. Instead, merges
    * are carried out as necessary.
    * <p>
    * This requires this index not be among those to be added, and the
    * upper bound* of those segment doc counts not exceed maxMergeDocs.
    *
    * <p>See {@link #addIndexes(Directory[])} for
    * details on transactional semantics, temporary free
    * space required in the Directory, and non-CFS segments
    * on an Exception.</p>
    */
   public synchronized void addIndexesNoOptimize(Directory[] dirs)
       throws IOException {
     // Adding indexes can be viewed as adding a sequence of segments S to
     // a sequence of segments T. Segments in T follow the invariants but
     // segments in S may not since they could come from multiple indexes.
     // Here is the merge algorithm for addIndexesNoOptimize():
     //
     // 1 Flush ram segments.
     // 2 Consider a combined sequence with segments from T followed
     //   by segments from S (same as current addIndexes(Directory[])).
     // 3 Assume the highest level for segments in S is h. Call
     //   maybeMergeSegments(), but instead of starting w/ lowerBound = -1
     //   and upperBound = maxBufferedDocs, start w/ lowerBound = -1 and
     //   upperBound = upperBound of level h. After this, the invariants
     //   are guaranteed except for the last < M segments whose levels <= h.
     // 4 If the invariants hold for the last < M segments whose levels <= h,
     //   if some of those < M segments are from S (not merged in step 3),
     //   properly copy them over*, otherwise done.
     //   Otherwise, simply merge those segments. If the merge results in
     //   a segment of level <= h, done. Otherwise, it's of level h+1 and call
     //   maybeMergeSegments() starting w/ upperBound = upperBound of level h+1.
     //
     // * Ideally, we want to simply copy a segment. However, directory does
     // not support copy yet. In addition, source may use compound file or not
     // and target may use compound file or not. So we use mergeSegments() to
     // copy a segment, which may cause doc count to change because deleted
     // docs are garbage collected.
 
     // 1 flush ram segments
 
     flushRamSegments();
 
     // 2 copy segment infos and find the highest level from dirs
     int start = segmentInfos.size();
     int startUpperBound = minMergeDocs;
 
     boolean success = false;
 
     startTransaction();
 
     try {
 
       try {
         for (int i = 0; i < dirs.length; i++) {
           if (directory == dirs[i]) {
             // cannot add this index: segments may be deleted in merge before added
             throw new IllegalArgumentException("Cannot add this index to itself");
           }
 
           SegmentInfos sis = new SegmentInfos(); // read infos from dir
           sis.read(dirs[i]);
           for (int j = 0; j < sis.size(); j++) {
             SegmentInfo info = sis.info(j);
             segmentInfos.addElement(info); // add each info
 
             while (startUpperBound < info.docCount) {
               startUpperBound *= mergeFactor; // find the highest level from dirs
               if (startUpperBound > maxMergeDocs) {
                 // upper bound cannot exceed maxMergeDocs
                 throw new IllegalArgumentException("Upper bound cannot exceed maxMergeDocs");
               }
             }
           }
         }
       } catch (IllegalArgumentException e) {
         for (int i = segmentInfos.size() - 1; i >= start; i--) {
           segmentInfos.remove(i);
         }
         throw e;
       }
 
       // 3 maybe merge segments starting from the highest level from dirs
       maybeMergeSegments(startUpperBound);
 
       // get the tail segments whose levels <= h
       int segmentCount = segmentInfos.size();
       int numTailSegments = 0;
       while (numTailSegments < segmentCount
              && startUpperBound >= segmentInfos.info(segmentCount - 1 - numTailSegments).docCount) {
         numTailSegments++;
       }
       if (numTailSegments == 0) {
         success = true;
         return;
       }
 
       // 4 make sure invariants hold for the tail segments whose levels <= h
       if (checkNonDecreasingLevels(segmentCount - numTailSegments)) {
         // identify the segments from S to be copied (not merged in 3)
         int numSegmentsToCopy = 0;
         while (numSegmentsToCopy < segmentCount
                && directory != segmentInfos.info(segmentCount - 1 - numSegmentsToCopy).dir) {
           numSegmentsToCopy++;
         }
         if (numSegmentsToCopy == 0) {
           success = true;
           return;
         }
 
         // copy those segments from S
         for (int i = segmentCount - numSegmentsToCopy; i < segmentCount; i++) {
           mergeSegments(segmentInfos, i, i + 1);
         }
         if (checkNonDecreasingLevels(segmentCount - numSegmentsToCopy)) {
           success = true;
           return;
         }
       }
 
       // invariants do not hold, simply merge those segments
       mergeSegments(segmentInfos, segmentCount - numTailSegments, segmentCount);
 
       // maybe merge segments again if necessary
       if (segmentInfos.info(segmentInfos.size() - 1).docCount > startUpperBound) {
         maybeMergeSegments(startUpperBound * mergeFactor);
       }
 
       success = true;
     } finally {
       if (success) {
         commitTransaction();
       } else {
         rollbackTransaction();
       }
     }
   }
 
   /** Merges the provided indexes into this index.
    * <p>After this completes, the index is optimized. </p>
    * <p>The provided IndexReaders are not closed.</p>
 
    * <p>See {@link #addIndexes(Directory[])} for
    * details on transactional semantics, temporary free
    * space required in the Directory, and non-CFS segments
    * on an Exception.</p>
    */
   public synchronized void addIndexes(IndexReader[] readers)
     throws IOException {
 
     optimize();					  // start with zero or 1 seg
 
     final String mergedName = newSegmentName();
     SegmentMerger merger = new SegmentMerger(this, mergedName);
 
     final Vector segmentsToDelete = new Vector();
     IndexReader sReader = null;
     if (segmentInfos.size() == 1){ // add existing index, if any
         sReader = SegmentReader.get(segmentInfos.info(0));
         merger.add(sReader);
         segmentsToDelete.addElement(sReader);   // queue segment for deletion
     }
 
     for (int i = 0; i < readers.length; i++)      // add new indexes
       merger.add(readers[i]);
 
     SegmentInfo info;
 
     String segmentsInfosFileName = segmentInfos.getCurrentSegmentFileName();
 
     boolean success = false;
 
     startTransaction();
 
     try {
       int docCount = merger.merge();                // merge 'em
 
       segmentInfos.setSize(0);                      // pop old infos & add new
       info = new SegmentInfo(mergedName, docCount, directory, false, true);
       segmentInfos.addElement(info);
       commitPending = true;
 
       if(sReader != null)
         sReader.close();
 
       success = true;
 
     } finally {
       if (!success) {
         rollbackTransaction();
       } else {
         commitTransaction();
       }
     }
 
     deleter.deleteFile(segmentsInfosFileName);    // delete old segments_N file
     deleter.deleteSegments(segmentsToDelete);     // delete now-unused segments
 
     if (useCompoundFile) {
       success = false;
 
       segmentsInfosFileName = segmentInfos.getCurrentSegmentFileName();
       Vector filesToDelete;
 
       startTransaction();
 
       try {
 
         filesToDelete = merger.createCompoundFile(mergedName + ".cfs");
 
         info.setUseCompoundFile(true);
         commitPending = true;
         success = true;
 
       } finally {
         if (!success) {
           rollbackTransaction();
         } else {
           commitTransaction();
         }
       }
 
       deleter.deleteFile(segmentsInfosFileName);  // delete old segments_N file
       deleter.deleteFiles(filesToDelete); // delete now unused files of segment 
     }
   }
 
   // Overview of merge policy:
   //
   // A flush is triggered either by close() or by the number of ram segments
   // reaching maxBufferedDocs. After a disk segment is created by the flush,
   // further merges may be triggered.
   //
   // LowerBound and upperBound set the limits on the doc count of a segment
   // which may be merged. Initially, lowerBound is set to 0 and upperBound
   // to maxBufferedDocs. Starting from the rightmost* segment whose doc count
   // > lowerBound and <= upperBound, count the number of consecutive segments
   // whose doc count <= upperBound.
   //
   // Case 1: number of worthy segments < mergeFactor, no merge, done.
   // Case 2: number of worthy segments == mergeFactor, merge these segments.
   //         If the doc count of the merged segment <= upperBound, done.
   //         Otherwise, set lowerBound to upperBound, and multiply upperBound
   //         by mergeFactor, go through the process again.
   // Case 3: number of worthy segments > mergeFactor (in the case mergeFactor
   //         M changes), merge the leftmost* M segments. If the doc count of
   //         the merged segment <= upperBound, consider the merged segment for
   //         further merges on this same level. Merge the now leftmost* M
   //         segments, and so on, until number of worthy segments < mergeFactor.
   //         If the doc count of all the merged segments <= upperBound, done.
   //         Otherwise, set lowerBound to upperBound, and multiply upperBound
   //         by mergeFactor, go through the process again.
   // Note that case 2 can be considerd as a special case of case 3.
   //
   // This merge policy guarantees two invariants if M does not change and
   // segment doc count is not reaching maxMergeDocs:
   // B for maxBufferedDocs, f(n) defined as ceil(log_M(ceil(n/B)))
   //      1: If i (left*) and i+1 (right*) are two consecutive segments of doc
   //         counts x and y, then f(x) >= f(y).
   //      2: The number of committed segments on the same level (f(n)) <= M.
 
  private final void maybeFlushRamSegments() throws IOException {
    if (ramSegmentInfos.size() >= minMergeDocs) {
       flushRamSegments();
     }
   }
 
   /** Expert:  Flushes all RAM-resident segments (buffered documents), then may merge segments. */
  public final synchronized void flushRamSegments() throws IOException {
    if (ramSegmentInfos.size() > 0) {
       mergeSegments(ramSegmentInfos, 0, ramSegmentInfos.size());
       maybeMergeSegments(minMergeDocs);
     }
   }
 
   /** Expert:  Return the total size of all index files currently cached in memory.
    * Useful for size management with flushRamDocs()
    */
   public final long ramSizeInBytes() {
     return ramDirectory.sizeInBytes();
   }
 
   /** Expert:  Return the number of documents whose segments are currently cached in memory.
    * Useful when calling flushRamSegments()
    */
   public final synchronized int numRamDocs() {
     return ramSegmentInfos.size();
   }
   
   /** Incremental segment merger.  */
   private final void maybeMergeSegments(int startUpperBound) throws IOException {
     long lowerBound = -1;
     long upperBound = startUpperBound;
 
     while (upperBound < maxMergeDocs) {
       int minSegment = segmentInfos.size();
       int maxSegment = -1;
 
       // find merge-worthy segments
       while (--minSegment >= 0) {
         SegmentInfo si = segmentInfos.info(minSegment);
 
         if (maxSegment == -1 && si.docCount > lowerBound && si.docCount <= upperBound) {
           // start from the rightmost* segment whose doc count is in bounds
           maxSegment = minSegment;
         } else if (si.docCount > upperBound) {
           // until the segment whose doc count exceeds upperBound
           break;
         }
       }
 
       minSegment++;
       maxSegment++;
       int numSegments = maxSegment - minSegment;
 
       if (numSegments < mergeFactor) {
         break;
       } else {
         boolean exceedsUpperLimit = false;
 
         // number of merge-worthy segments may exceed mergeFactor when
         // mergeFactor and/or maxBufferedDocs change(s)
         while (numSegments >= mergeFactor) {
           // merge the leftmost* mergeFactor segments
 
           int docCount = mergeSegments(segmentInfos, minSegment, minSegment + mergeFactor);
           numSegments -= mergeFactor;
 
           if (docCount > upperBound) {
             // continue to merge the rest of the worthy segments on this level
             minSegment++;
             exceedsUpperLimit = true;
           } else {
             // if the merged segment does not exceed upperBound, consider
             // this segment for further merges on this same level
             numSegments++;
           }
         }
 
         if (!exceedsUpperLimit) {
           // if none of the merged segments exceed upperBound, done
           break;
         }
       }
 
       lowerBound = upperBound;
       upperBound *= mergeFactor;
     }
   }
 
   /**
    * Merges the named range of segments, replacing them in the stack with a
    * single segment.
    */
   private final int mergeSegments(SegmentInfos sourceSegments, int minSegment, int end)
     throws IOException {
 
     final String mergedName = newSegmentName();
    if (infoStream != null) infoStream.print("merging segments");
    SegmentMerger merger = new SegmentMerger(this, mergedName);
     
     final Vector segmentsToDelete = new Vector();
 
     String segmentsInfosFileName = segmentInfos.getCurrentSegmentFileName();
     String nextSegmentsFileName = segmentInfos.getNextSegmentFileName();
 
     SegmentInfo newSegment = null;
 
    int mergedDocCount;
 
     // This is try/finally to make sure merger's readers are closed:
     try {
 
       for (int i = minSegment; i < end; i++) {
         SegmentInfo si = sourceSegments.info(i);
         if (infoStream != null)
           infoStream.print(" " + si.name + " (" + si.docCount + " docs)");
        IndexReader reader = SegmentReader.get(si);
         merger.add(reader);
         if ((reader.directory() == this.directory) || // if we own the directory
             (reader.directory() == this.ramDirectory))
           segmentsToDelete.addElement(reader);   // queue segment for deletion
       }
 
       SegmentInfos rollback = null;
       boolean success = false;
 
       // This is try/finally to rollback our internal state
       // if we hit exception when doing the merge:
       try {
 
         mergedDocCount = merger.merge();
 
         if (infoStream != null) {
           infoStream.println(" into "+mergedName+" ("+mergedDocCount+" docs)");
         }
 
         newSegment = new SegmentInfo(mergedName, mergedDocCount,
                                      directory, false, true);
 

        if (sourceSegments == ramSegmentInfos) {
          segmentInfos.addElement(newSegment);
        } else {

          if (!inTransaction) {
             // Now save the SegmentInfo instances that
             // we are replacing:
             rollback = (SegmentInfos) segmentInfos.clone();
           }
 
           for (int i = end-1; i > minSegment; i--)     // remove old infos & add new
             sourceSegments.remove(i);
 
           segmentInfos.set(minSegment, newSegment);
         }
 
         if (!inTransaction) {
           segmentInfos.write(directory);     // commit before deleting
         } else {
           commitPending = true;
         }
 
         success = true;
 
       } finally {
 
         if (success) {
           // The non-ram-segments case is already committed
           // (above), so all the remains for ram segments case
           // is to clear the ram segments:
           if (sourceSegments == ramSegmentInfos) {
             ramSegmentInfos.removeAllElements();
           }
         } else if (!inTransaction) {  
 
           // Must rollback so our state matches index:
 
          if (sourceSegments == ramSegmentInfos) {
             // Simple case: newSegment may or may not have
             // been added to the end of our segment infos,
             // so just check & remove if so:
             if (newSegment != null && 
                 segmentInfos.size() > 0 && 
                 segmentInfos.info(segmentInfos.size()-1) == newSegment) {
               segmentInfos.remove(segmentInfos.size()-1);
             }
           } else if (rollback != null) {
             // Rollback the individual SegmentInfo
             // instances, but keep original SegmentInfos
             // instance (so we don't try to write again the
             // same segments_N file -- write once):
             segmentInfos.clear();
             segmentInfos.addAll(rollback);
           }
 
           // Delete any partially created files:
           deleter.deleteFile(nextSegmentsFileName);
           deleter.findDeletableFiles();
           deleter.deleteFiles();
         }
       }
     } finally {
       // close readers before we attempt to delete now-obsolete segments
      merger.closeReaders();
     }
 
     if (!inTransaction) {
       deleter.deleteFile(segmentsInfosFileName);    // delete old segments_N file
       deleter.deleteSegments(segmentsToDelete);     // delete now-unused segments
     } else {
       deleter.addPendingFile(segmentsInfosFileName);    // delete old segments_N file
       deleter.deleteSegments(segmentsToDelete, protectedSegments);     // delete now-unused segments
     }
 
    if (useCompoundFile) {
 
       segmentsInfosFileName = nextSegmentsFileName;
       nextSegmentsFileName = segmentInfos.getNextSegmentFileName();
 
       Vector filesToDelete;
 
       boolean success = false;
 
       try {
 
         filesToDelete = merger.createCompoundFile(mergedName + ".cfs");
         newSegment.setUseCompoundFile(true);
         if (!inTransaction) {
           segmentInfos.write(directory);     // commit again so readers know we've switched this segment to a compound file
         }
         success = true;
 
       } finally {
         if (!success && !inTransaction) {  
           // Must rollback:
           newSegment.setUseCompoundFile(false);
           deleter.deleteFile(mergedName + ".cfs");
           deleter.deleteFile(nextSegmentsFileName);
         }
       }
 
       if (!inTransaction) {
         deleter.deleteFile(segmentsInfosFileName);  // delete old segments_N file
       }
 
       // We can delete these segments whether or not we are
       // in a transaction because we had just written them
       // above so they can't need protection by the
       // transaction:
       deleter.deleteFiles(filesToDelete);  // delete now-unused segments
     }
 
     return mergedDocCount;
   }
 
   private final boolean checkNonDecreasingLevels(int start) {
     int lowerBound = -1;
     int upperBound = minMergeDocs;
 
     for (int i = segmentInfos.size() - 1; i >= start; i--) {
       int docCount = segmentInfos.info(i).docCount;
       if (docCount <= lowerBound) {
         return false;
       }
 
       while (docCount > upperBound) {
         lowerBound = upperBound;
         upperBound *= mergeFactor;
       }
     }
     return true;
   }
 }
