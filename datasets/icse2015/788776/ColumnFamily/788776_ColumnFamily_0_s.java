 /**
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
 
 package org.apache.cassandra.db;
 
 import java.io.DataInputStream;
 import java.io.DataOutputStream;
 import java.io.IOException;
 import java.lang.reflect.Proxy;
 import java.util.Collection;
 import java.util.Comparator;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 import java.util.SortedSet;
 import java.util.concurrent.atomic.AtomicInteger;
 
 import org.apache.commons.lang.StringUtils;
 import org.apache.commons.lang.ArrayUtils;
 import org.apache.log4j.Logger;
 
 import org.apache.cassandra.config.DatabaseDescriptor;
 import org.apache.cassandra.utils.FBUtilities;
 
 /**
  * Author : Avinash Lakshman ( alakshman@facebook.com) & Prashant Malik ( pmalik@facebook.com )
  */
 public final class ColumnFamily
 {
     /* The column serializer for this Column Family. Create based on config. */
     private static ICompactSerializer2<ColumnFamily> serializer_;
     public static final short utfPrefix_ = 2;   
 
     private static Logger logger_ = Logger.getLogger( ColumnFamily.class );
     private static Map<String, String> columnTypes_ = new HashMap<String, String>();
     private static Map<String, String> indexTypes_ = new HashMap<String, String>();
     private String type_;
     private String table_;
 
     static
     {
         serializer_ = new ColumnFamilySerializer();
         /* TODO: These are the various column types. Hard coded for now. */
         columnTypes_.put("Standard", "Standard");
         columnTypes_.put("Super", "Super");
 
         indexTypes_.put("Name", "Name");
         indexTypes_.put("Time", "Time");
     }
 
     public static ICompactSerializer2<ColumnFamily> serializer()
     {
         return serializer_;
     }
 
     /*
      * This method returns the serializer whose methods are
      * preprocessed by a dynamic proxy.
     */
     public static ICompactSerializer2<ColumnFamily> serializerWithIndexes()
     {
         return (ICompactSerializer2<ColumnFamily>)Proxy.newProxyInstance( ColumnFamily.class.getClassLoader(), new Class[]{ICompactSerializer2.class}, new CompactSerializerInvocationHandler<ColumnFamily>(serializer_) );
     }
 
     public static String getColumnType(String key)
     {
     	if ( key == null )
     		return columnTypes_.get("Standard");
     	return columnTypes_.get(key);
     }
 
     public static String getColumnSortProperty(String columnIndexProperty)
     {
     	if ( columnIndexProperty == null )
     		return indexTypes_.get("Time");
         return indexTypes_.get(columnIndexProperty);
     }
 
     public static ColumnFamily create(String tableName, String cfName)
     {
         Comparator<IColumn> comparator;
         String columnType = DatabaseDescriptor.getColumnFamilyType(tableName, cfName);
         if (DatabaseDescriptor.isNameSortingEnabled(tableName, cfName))
         {
             comparator = ColumnComparatorFactory.getComparator(ColumnComparatorFactory.ComparatorType.NAME);
         }
         /* if this columnfamily has simple columns, and no index on name sort by timestamp */
         else
         {
             comparator = ColumnComparatorFactory.getComparator(ColumnComparatorFactory.ComparatorType.TIMESTAMP);
         }
         return new ColumnFamily(cfName, columnType, comparator);
     }
 
     private transient AbstractColumnFactory columnFactory_;
 
     private String name_;
 
     private transient ICompactSerializer2<IColumn> columnSerializer_;
     private long markedForDeleteAt = Long.MIN_VALUE;
     private int localDeletionTime = Integer.MIN_VALUE;
     private AtomicInteger size_ = new AtomicInteger(0);
     private EfficientBidiMap columns_;
 
     public ColumnFamily(String cfName, String columnType, Comparator<IColumn> comparator)
     {
         name_ = cfName;
         type_ = columnType;
         columnFactory_ = AbstractColumnFactory.getColumnFactory(columnType);
         columnSerializer_ = columnFactory_.createColumnSerializer();
         if(columns_ == null)
             columns_ = new EfficientBidiMap(comparator);
     }
 
     public ColumnFamily(String cfName, String columnType, ColumnComparatorFactory.ComparatorType indexType)
     {
         this(cfName, columnType, ColumnComparatorFactory.getComparator(indexType));
     }
 
     ColumnFamily cloneMeShallow()
     {
         ColumnFamily cf = new ColumnFamily(name_, type_, getComparator());
         cf.markedForDeleteAt = markedForDeleteAt;
         cf.localDeletionTime = localDeletionTime;
         return cf;
     }
 
     ColumnFamily cloneMe()
     {
         ColumnFamily cf = cloneMeShallow();
         cf.columns_ = columns_.cloneMe();
     	return cf;
     }
 
     public String name()
     {
         return name_;
     }
 
     /*
      *  We need to go through each column
      *  in the column family and resolve it before adding
     */
     void addColumns(ColumnFamily cf)
     {
         for (IColumn column : cf.getAllColumns())
         {
             addColumn(column);
         }
     }
 
     public ICompactSerializer2<IColumn> getColumnSerializer()
     {
     	return columnSerializer_;
     }
 
     public void addColumn(String name)
     {
     	addColumn(columnFactory_.createColumn(name));
     }
 
     int getColumnCount()
     {
     	int count = 0;
     	Map<String, IColumn> columns = columns_.getColumns();
     	if( columns != null )
     	{
     		if(!isSuper())
     		{
     			count = columns.size();
     		}
     		else
     		{
     			Collection<IColumn> values = columns.values();
 		    	for(IColumn column: values)
 		    	{
 		    		count += column.getObjectCount();
 		    	}
     		}
     	}
     	return count;
     }
 
     public boolean isSuper()
     {
         return type_.equals("Super");
     }
 
     public void addColumn(String name, byte[] value)
     {
     	addColumn(name, value, 0);
     }
 
     public void addColumn(String name, byte[] value, long timestamp)
     {
         addColumn(name, value, timestamp, false);
     }
 
     public void addColumn(String name, byte[] value, long timestamp, boolean deleted)
 	{
 		IColumn column = columnFactory_.createColumn(name, value, timestamp, deleted);
 		addColumn(column);
     }
 
     void clear()
     {
         logger_.debug("clearing");
     	columns_.clear();
     	size_.set(0);
     }
 
     /*
      * If we find an old column that has the same name
      * the ask it to resolve itself else add the new column .
     */
     void addColumn(IColumn column)
     {
         String name = column.name();
         IColumn oldColumn = columns_.get(name);
         if (oldColumn != null)
         {
             if (oldColumn instanceof SuperColumn)
             {
                 int oldSize = oldColumn.size();
                 ((SuperColumn) oldColumn).putColumn(column);
                 size_.addAndGet(oldColumn.size() - oldSize);
             }
             else
             {
                 if (((Column)oldColumn).comparePriority((Column)column) <= 0)
                 {
                     columns_.put(name, column);
                     size_.addAndGet(column.size());
                 }
             }
         }
         else
         {
             size_.addAndGet(column.size());
             columns_.put(name, column);
         }
     }
 
     public IColumn getColumn(String name)
     {
         return columns_.get( name );
     }
 
     public SortedSet<IColumn> getAllColumns()
     {
         return columns_.getSortedColumns();
     }
 
     public Map<String, IColumn> getColumns()
     {
         return columns_.getColumns();
     }
 
     public void remove(String columnName)
     {
     	columns_.remove(columnName);
     }
 
     public void delete(int localtime, long timestamp)
     {
         localDeletionTime = localtime;
         markedForDeleteAt = timestamp;
     }
 
     public void delete(ColumnFamily cf2)
     {
         delete(Math.max(getLocalDeletionTime(), cf2.getLocalDeletionTime()),
                Math.max(getMarkedForDeleteAt(), cf2.getMarkedForDeleteAt()));
     }
 
     public boolean isMarkedForDelete()
     {
         return markedForDeleteAt > Long.MIN_VALUE;
     }
 
     public String getTable() {
         return table_;
     }
 
     /*
      * This function will calculate the difference between 2 column families.
      * The external input is assumed to be a superset of internal.
      */
     ColumnFamily diff(ColumnFamily cfComposite)
     {
     	ColumnFamily cfDiff = new ColumnFamily(cfComposite.name(), cfComposite.type_, getComparator());
         if (cfComposite.getMarkedForDeleteAt() > getMarkedForDeleteAt())
         {
             cfDiff.delete(cfComposite.getLocalDeletionTime(), cfComposite.getMarkedForDeleteAt());
         }
 
         // (don't need to worry about cfNew containing IColumns that are shadowed by
         // the delete tombstone, since cfNew was generated by CF.resolve, which
         // takes care of those for us.)
         Map<String, IColumn> columns = cfComposite.getColumns();
         Set<String> cNames = columns.keySet();
         for ( String cName : cNames )
         {
         	IColumn columnInternal = columns_.get(cName);
         	IColumn columnExternal = columns.get(cName);
         	if( columnInternal == null )
         	{
         		cfDiff.addColumn(columnExternal);
         	}
         	else
         	{
             	IColumn columnDiff = columnInternal.diff(columnExternal);
         		if(columnDiff != null)
         		{
         			cfDiff.addColumn(columnDiff);
         		}
         	}
         }
 
         if (!cfDiff.getColumns().isEmpty() || cfDiff.isMarkedForDelete())
         	return cfDiff;
         else
         	return null;
     }
 
     private Comparator<IColumn> getComparator()
     {
         return columns_.getComparator();
     }
 
     public ColumnComparatorFactory.ComparatorType getComparatorType()
     {
         return getComparator() == ColumnComparatorFactory.nameComparator_
                ? ColumnComparatorFactory.ComparatorType.NAME
                : ColumnComparatorFactory.ComparatorType.TIMESTAMP;
     }
 
     int size()
     {
         if ( size_.get() == 0 )
         {
             Set<String> cNames = columns_.getColumns().keySet();
             for ( String cName : cNames )
             {
                 size_.addAndGet(columns_.get(cName).size());
             }
         }
         return size_.get();
     }
 
     public int hashCode()
     {
         return name().hashCode();
     }
 
     public boolean equals(Object o)
     {
         if ( !(o instanceof ColumnFamily) )
             return false;
         ColumnFamily cf = (ColumnFamily)o;
         return name().equals(cf.name());
     }
 
     public String toString()
     {
     	StringBuilder sb = new StringBuilder();
         sb.append("ColumnFamily(");
     	sb.append(name_);
 
         if (isMarkedForDelete()) {
             sb.append(" -delete at " + getMarkedForDeleteAt() + "-");
         }
 
     	sb.append(" [");
         sb.append(StringUtils.join(getAllColumns(), ", "));
         sb.append("])");
 
     	return sb.toString();
     }
 
     public byte[] digest()
     {
     	Set<IColumn> columns = columns_.getSortedColumns();
     	byte[] xorHash = ArrayUtils.EMPTY_BYTE_ARRAY;
         for(IColumn column : columns)
     	{
     		if(xorHash.length == 0)
     		{
     			xorHash = column.digest();
     		}
     		else
     		{
                 xorHash = FBUtilities.xor(xorHash, column.digest());
     		}
     	}
     	return xorHash;
     }
 
     public long getMarkedForDeleteAt()
     {
         return markedForDeleteAt;
     }
 
     public int getLocalDeletionTime()
     {
         return localDeletionTime;
     }
 
     public String type()
     {
         return type_;
     }
 
     /** merge all columnFamilies into a single instance, with only the newest versions of columns preserved. */
     static ColumnFamily resolve(List<ColumnFamily> columnFamilies)
     {
         int size = columnFamilies.size();
         if (size == 0)
             return null;
 
         // start from nothing so that we don't include potential deleted columns from the first instance
         ColumnFamily cf0 = columnFamilies.get(0);
         ColumnFamily cf = cf0.cloneMeShallow();
 
         // merge
         for (ColumnFamily cf2 : columnFamilies)
         {
             assert cf.name().equals(cf2.name());
             cf.addColumns(cf2);
             cf.delete(cf2);
         }
         return cf;
     }
 
     public static class ColumnFamilySerializer implements ICompactSerializer2<ColumnFamily>
     {
         /*
          * We are going to create indexes, and write out that information as well. The format
          * of the data serialized is as follows.
          *
          * 1) Without indexes:
          *  // written by the data
          * 	<boolean false (index is not present)>
          * 	<column family id>
          * 	<is marked for delete>
          * 	<total number of columns>
          * 	<columns data>
 
          * 	<boolean true (index is present)>
          *
          *  This part is written by the column indexer
          * 	<size of index in bytes>
          * 	<list of column names and their offsets relative to the first column>
          *
          *  <size of the cf in bytes>
          * 	<column family id>
          * 	<is marked for delete>
          * 	<total number of columns>
          * 	<columns data>
         */
         public void serialize(ColumnFamily columnFamily, DataOutputStream dos) throws IOException
         {
             Collection<IColumn> columns = columnFamily.getAllColumns();
 
             dos.writeUTF(columnFamily.name());
             dos.writeUTF(columnFamily.type_);
             dos.writeInt(columnFamily.getComparatorType().ordinal());
             dos.writeInt(columnFamily.localDeletionTime);
             dos.writeLong(columnFamily.markedForDeleteAt);
 
             dos.writeInt(columns.size());
             for ( IColumn column : columns )
             {
                 columnFamily.getColumnSerializer().serialize(column, dos);
             }
         }
 
         /*
          * Use this method to create a bare bones Column Family. This column family
          * does not have any of the Column information.
         */
         private ColumnFamily defreezeColumnFamily(DataInputStream dis) throws IOException
         {
             ColumnFamily cf = new ColumnFamily(dis.readUTF(),
                                                dis.readUTF(),
                                                ColumnComparatorFactory.ComparatorType.values()[dis.readInt()]);
             cf.delete(dis.readInt(), dis.readLong());
             return cf;
         }
 
         public ColumnFamily deserialize(DataInputStream dis) throws IOException
         {
             ColumnFamily cf = defreezeColumnFamily(dis);
             int size = dis.readInt();
             IColumn column;
             for (int i = 0; i < size; ++i)
             {
                 column = cf.getColumnSerializer().deserialize(dis);
                 cf.addColumn(column);
             }
             return cf;
         }
 
         /*
          * This version of deserialize is used when we need a specific set if columns for
          * a column family specified in the name cfName parameter.
         */
         public ColumnFamily deserialize(DataInputStream dis, IFilter filter) throws IOException
         {
             ColumnFamily cf = defreezeColumnFamily(dis);
             int size = dis.readInt();
             IColumn column = null;
             for ( int i = 0; i < size; ++i )
             {
                 column = cf.getColumnSerializer().deserialize(dis, filter);
                 if(column != null)
                 {
                     cf.addColumn(column);
                    if(filter.isDone())
                    {
                        break;
                    }
                 }
             }
             return cf;
         }
 
         /*
          * Deserialize a particular column or super column or the entire columnfamily given a : seprated name
          * name could be of the form cf:superColumn:column  or cf:column or cf
          */
         public ColumnFamily deserialize(DataInputStream dis, String name, IFilter filter) throws IOException
         {
             String[] names = RowMutation.getColumnAndColumnFamily(name);
             String columnName = "";
             if ( names.length == 1 )
                 return deserialize(dis, filter);
             if( names.length == 2 )
                 columnName = names[1];
             if( names.length == 3 )
                 columnName = names[1]+ ":" + names[2];
 
             ColumnFamily cf = defreezeColumnFamily(dis);
             /* read the number of columns */
             int size = dis.readInt();
             for ( int i = 0; i < size; ++i )
             {
                 IColumn column = cf.getColumnSerializer().deserialize(dis, columnName, filter);
                 if ( column != null )
                 {
                     cf.addColumn(column);
                     break;
                 }
             }
             return cf;
         }
 
         public void skip(DataInputStream dis) throws IOException
         {
             throw new UnsupportedOperationException("This operation is not yet supported.");
         }
     }
 
 }
 
