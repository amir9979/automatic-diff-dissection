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
 
 /**
  * The MBean interface for ColumnFamilyStore
  * 
  * @author Eric Evans
  *
  */
 public interface ColumnFamilyStoreMBean
 {
     /**
      * Returns the total amount of data stored in the memtable, including
      * column related overhead.
      * 
      * @return The size in bytes.
      */
     public int getMemtableDataSize();
     
     /**
      * Returns the total number of columns present in the memtable.
      * 
      * @return The number of columns.
      */
     public int getMemtableColumnsCount();
     
     /**
      * Returns the number of times that a flush has resulted in the
      * memtable being switched out.
      *
      * @return the number of memtable switches
      */
     public int getMemtableSwitchCount();
 
     /**
      * Triggers an immediate memtable flush.
      */
     public void forceFlush();
 }
