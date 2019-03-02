/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
 package org.apache.cassandra.db;
 
 import java.io.IOException;
 import java.util.concurrent.ExecutionException;
 
 import org.junit.Test;
 
 import static junit.framework.Assert.assertNull;
 
 public class RemoveSubColumnTest
 {
     @Test
     public void testRemoveSubColumn() throws IOException, ExecutionException, InterruptedException
     {
         Table table = Table.open("Table1");
         ColumnFamilyStore store = table.getColumnFamilyStore("Super1");
         RowMutation rm;
 
         // add data
         rm = new RowMutation("Table1", "key1");
         rm.add("Super1:SC1:Column1", "asdf".getBytes(), 0);
         rm.apply();
         store.forceBlockingFlush();
 
         // remove
         rm = new RowMutation("Table1", "key1");
         rm.delete("Super1:SC1:Column1", 1);
         rm.apply();
 
         ColumnFamily retrieved = store.getColumnFamily("key1", "Super1:SC1", new IdentityFilter());
         assert retrieved.getColumn("SC1").getSubColumn("Column1").isMarkedForDelete();
         assertNull(ColumnFamilyStore.removeDeleted(retrieved, Integer.MAX_VALUE));
     }
 }
