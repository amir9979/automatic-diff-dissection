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
 
 import java.util.List;
 import java.util.ArrayList;
 import java.util.Collection;
 import java.util.Collections;
 import java.util.Arrays;
 import java.io.IOException;
 
 import org.apache.cassandra.io.DataInputBuffer;
 import org.apache.cassandra.io.DataOutputBuffer;
 import org.apache.cassandra.net.Message;
 import org.apache.cassandra.service.StorageService;
 
 public class RangeReply
 {
     public final List<String> keys;
 
     public RangeReply(List<String> keys)
     {
         this.keys = Collections.unmodifiableList(keys);
     }
 
     public Message getReply(Message originalMessage)
     {
         DataOutputBuffer dob = new DataOutputBuffer();
         for (String key : keys)
         {
             try
             {
                 dob.writeUTF(key);
             }
             catch (IOException e)
             {
                 throw new RuntimeException(e);
             }
         }
         byte[] data = Arrays.copyOf(dob.getData(), dob.getLength());
         return originalMessage.getReply(StorageService.getLocalStorageEndPoint(), data);
     }
 
     public static RangeReply read(byte[] body) throws IOException
     {
         DataInputBuffer bufIn = new DataInputBuffer();
         bufIn.reset(body, body.length);
 
         List<String> keys = new ArrayList<String>();
         while (bufIn.getPosition() < body.length)
         {
             keys.add(bufIn.readUTF());
         }
 
         return new RangeReply(keys);
     }
 }
