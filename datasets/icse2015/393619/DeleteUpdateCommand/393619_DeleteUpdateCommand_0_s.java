 /**
  * Copyright 2006 The Apache Software Foundation
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
 
 package org.apache.solr.update;
 /**
  * @author yonik
  * @version $Id$
  */
 public class DeleteUpdateCommand extends UpdateCommand {
  public String id;
  public String query;
   public boolean fromPending;
   public boolean fromCommitted;
 
   public DeleteUpdateCommand() {
     super("delete");
   }
 
   public String toString() {
     StringBuilder sb = new StringBuilder(commandName);
     sb.append(':');
     if (id!=null) sb.append("id=").append(id);
     else sb.append("query=`").append(query).append('`');
     sb.append(",fromPending=").append(fromPending);
     sb.append(",fromCommitted=").append(fromCommitted);
     return sb.toString();
   }
 }
