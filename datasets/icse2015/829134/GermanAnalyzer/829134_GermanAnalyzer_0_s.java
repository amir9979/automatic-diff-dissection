 package org.apache.lucene.analysis.de;
 // This file is encoded in UTF-8
 
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
 
 import java.io.File;
 import java.io.IOException;
 import java.io.Reader;
 import java.util.HashSet;
 import java.util.Map;
 import java.util.Set;
 
 import org.apache.lucene.analysis.Analyzer;
 import org.apache.lucene.analysis.LowerCaseFilter;
 import org.apache.lucene.analysis.StopFilter;
 import org.apache.lucene.analysis.TokenStream;
 import org.apache.lucene.analysis.Tokenizer;
 import org.apache.lucene.analysis.WordlistLoader;
 import org.apache.lucene.analysis.standard.StandardFilter;
 import org.apache.lucene.analysis.standard.StandardTokenizer;
 
 /**
  * {@link Analyzer} for German language. 
  * <p>
  * Supports an external list of stopwords (words that
  * will not be indexed at all) and an external list of exclusions (word that will
  * not be stemmed, but indexed).
  * A default set of stopwords is used unless an alternative list is specified, but the
  * exclusion list is empty by default.
  * </p>
  * 
  * @version $Id$
  */
 public class GermanAnalyzer extends Analyzer {
   
   /**
    * List of typical german stopwords.
    */
   public final static String[] GERMAN_STOP_WORDS = {
     "einer", "eine", "eines", "einem", "einen",
     "der", "die", "das", "dass", "daÃ",
     "du", "er", "sie", "es",
     "was", "wer", "wie", "wir",
     "und", "oder", "ohne", "mit",
     "am", "im", "in", "aus", "auf",
     "ist", "sein", "war", "wird",
     "ihr", "ihre", "ihres",
     "als", "fÃ¼r", "von", "mit",
     "dich", "dir", "mich", "mir",
     "mein", "sein", "kein",
     "durch", "wegen", "wird"
   };
 
   /**
    * Contains the stopwords used with the {@link StopFilter}.
    */
   private Set stopSet = new HashSet();
 
   /**
    * Contains words that should be indexed but not stemmed.
    */
   private Set exclusionSet = new HashSet();
 
   /**
    * Builds an analyzer with the default stop words:
    * {@link #GERMAN_STOP_WORDS}.
    */
   public GermanAnalyzer() {
     stopSet = StopFilter.makeStopSet(GERMAN_STOP_WORDS);
     setOverridesTokenStreamMethod(GermanAnalyzer.class);
   }
 
   /**
    * Builds an analyzer with the given stop words.
    */
   public GermanAnalyzer(String[] stopwords) {
     stopSet = StopFilter.makeStopSet(stopwords);
     setOverridesTokenStreamMethod(GermanAnalyzer.class);
   }
 
   /**
    * Builds an analyzer with the given stop words.
    */
   public GermanAnalyzer(Map stopwords) {
     stopSet = new HashSet(stopwords.keySet());
     setOverridesTokenStreamMethod(GermanAnalyzer.class);
   }
 
   /**
    * Builds an analyzer with the given stop words.
    */
   public GermanAnalyzer(File stopwords) throws IOException {
     stopSet = WordlistLoader.getWordSet(stopwords);
     setOverridesTokenStreamMethod(GermanAnalyzer.class);
   }
 
   /**
    * Builds an exclusionlist from an array of Strings.
    */
   public void setStemExclusionTable(String[] exclusionlist) {
     exclusionSet = StopFilter.makeStopSet(exclusionlist);
     setPreviousTokenStream(null); // force a new stemmer to be created
   }
 
   /**
    * Builds an exclusionlist from a {@link Map}
    */
   public void setStemExclusionTable(Map exclusionlist) {
     exclusionSet = new HashSet(exclusionlist.keySet());
     setPreviousTokenStream(null); // force a new stemmer to be created
   }
 
   /**
    * Builds an exclusionlist from the words contained in the given file.
    */
   public void setStemExclusionTable(File exclusionlist) throws IOException {
     exclusionSet = WordlistLoader.getWordSet(exclusionlist);
     setPreviousTokenStream(null); // force a new stemmer to be created
   }
 
   /**
    * Creates a {@link TokenStream} which tokenizes all the text in the provided {@link Reader}.
    *
    * @return A {@link TokenStream} built from a {@link StandardTokenizer} filtered with
    *         {@link StandardFilter}, {@link LowerCaseFilter}, {@link StopFilter}, and
    *         {@link GermanStemFilter}
    */
   public TokenStream tokenStream(String fieldName, Reader reader) {
    TokenStream result = new StandardTokenizer(reader);
     result = new StandardFilter(result);
     result = new LowerCaseFilter(result);
    result = new StopFilter(result, stopSet);
     result = new GermanStemFilter(result, exclusionSet);
     return result;
   }
   
   private class SavedStreams {
     Tokenizer source;
     TokenStream result;
   };
   
   /**
    * Returns a (possibly reused) {@link TokenStream} which tokenizes all the text 
    * in the provided {@link Reader}.
    *
    * @return A {@link TokenStream} built from a {@link StandardTokenizer} filtered with
    *         {@link StandardFilter}, {@link LowerCaseFilter}, {@link StopFilter}, and
    *         {@link GermanStemFilter}
    */
   public TokenStream reusableTokenStream(String fieldName, Reader reader) throws IOException {
     if (overridesTokenStreamMethod) {
       // LUCENE-1678: force fallback to tokenStream() if we
       // have been subclassed and that subclass overrides
       // tokenStream but not reusableTokenStream
       return tokenStream(fieldName, reader);
     }
     
     SavedStreams streams = (SavedStreams) getPreviousTokenStream();
     if (streams == null) {
       streams = new SavedStreams();
      streams.source = new StandardTokenizer(reader);
       streams.result = new StandardFilter(streams.source);
       streams.result = new LowerCaseFilter(streams.result);
      streams.result = new StopFilter(streams.result, stopSet);
       streams.result = new GermanStemFilter(streams.result, exclusionSet);
       setPreviousTokenStream(streams);
     } else {
       streams.source.reset(reader);
     }
     return streams.result;
   }
 }
