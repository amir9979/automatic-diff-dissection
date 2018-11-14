 package org.apache.cassandra.locator;
 
 import java.io.IOException;
 import java.io.IOError;
 import java.net.InetAddress;
 import java.net.UnknownHostException;
 import java.util.*;
 import java.util.Map.Entry;
 
 import org.apache.cassandra.config.DatabaseDescriptor;
 import org.apache.cassandra.dht.IPartitioner;
 import org.apache.cassandra.dht.Token;
 import org.apache.cassandra.service.*;
 
 /**
  * This Stategy is little diffrent than the Rack aware Statergy. If there is
  * replication factor is N. We will make sure that (N-1)%2 of the nodes are in
  * other Datacenter.... For example if we have 5 nodes this stategy will make
  * sure to make 2 copies out of 5 in other dataceneter.
  * <p/>
  * This class also caches the EndPoints and invalidates the cache if there is a
  * change in the number of tokens.
  */
 public class DatacenterShardStategy extends AbstractReplicationStrategy
 {
     private static Map<String, List<Token>> dcMap = new HashMap<String, List<Token>>();
     private static Map<String, Integer> dcReplicationFactor = new HashMap<String, Integer>();
     private static Map<String, Integer> quorumRepFactor = new HashMap<String, Integer>();
     private static int locQFactor = 0;
     private static DatacenterEndPointSnitch endPointSnitch;
     ArrayList<Token> tokens;
 
     private List<InetAddress> localEndPoints = new ArrayList<InetAddress>();
 
     private List<InetAddress> getLocalEndPoints()
     {
         return new ArrayList<InetAddress>(localEndPoints);
     }
 
     private Map<String, Integer> getQuorumRepFactor()
     {
         return new HashMap<String, Integer>(quorumRepFactor);
     }
 
     /**
      * This Method will get the required information of the EndPoint from the
      * DataCenterEndPointSnitch and poopulates this singleton class.
      *
      * @param tokenToEndPointMap - Provided the endpoint map which will be mapped with the DC's
      */
    private void loadEndPoints(Map<Token, InetAddress> tokenToEndPointMap, Collection<Token> tokens) throws IOException
     {
         endPointSnitch = (DatacenterEndPointSnitch) StorageService.instance().getEndPointSnitch();
         this.tokens = new ArrayList<Token>(tokens);
         String localDC = endPointSnitch.getLocation(InetAddress.getLocalHost());
         for (Token token : this.tokens)
         {
             InetAddress endPoint = tokenToEndPointMap.get(token);
             String dataCenter = endPointSnitch.getLocation(endPoint);
             if (dataCenter.equals(localDC))
             {
                 localEndPoints.add(endPoint);
             }
             List<Token> lst = dcMap.get(dataCenter);
             if (lst == null)
             {
                 lst = new ArrayList<Token>();
             }
             lst.add(token);
             dcMap.put(dataCenter, lst);
         }
         for (Entry<String, List<Token>> entry : dcMap.entrySet())
         {
             List<Token> valueList = entry.getValue();
             Collections.sort(valueList);
             dcMap.put(entry.getKey(), valueList);
         }
         dcReplicationFactor = endPointSnitch.getMapReplicationFactor();
         for (Entry<String, Integer> entry : dcReplicationFactor.entrySet())
         {
             String datacenter = entry.getKey();
             int qFactor = (entry.getValue() / 2 + 1);
             quorumRepFactor.put(datacenter, qFactor);
             if (datacenter.equals(localDC))
             {
                 locQFactor = qFactor;
             }
         }
     }
 
     public DatacenterShardStategy(TokenMetadata tokenMetadata, IPartitioner<Token> partitioner, int replicas, int storagePort)
     throws UnknownHostException
     {
         super(tokenMetadata, partitioner, replicas, storagePort);
         assert (DatabaseDescriptor.getEndPointSnitch() instanceof DatacenterEndPointSnitch);
     }
 
     @Override
     public ArrayList<InetAddress> getNaturalEndpoints(Token searchToken, Map<Token, InetAddress> tokenToEndPointMap)
     {
         try
         {
             return getNaturalEndpointsInternal(searchToken, tokenToEndPointMap);
         }
         catch (IOException e)
         {
              throw new IOError(e);
         }
     }
 
     private ArrayList<InetAddress> getNaturalEndpointsInternal(Token searchToken, Map<Token, InetAddress> tokenToEndPointMap) throws IOException
     {
         ArrayList<InetAddress> endpoints = new ArrayList<InetAddress>();
         if (null == tokens || this.tokens.size() != tokenToEndPointMap.keySet().size())
         {
             loadEndPoints(tokenToEndPointMap, tokenToEndPointMap.keySet());
         }
 
         for (String dc : dcMap.keySet())
         {
             int foundCount = 0;
             ArrayList<InetAddress> forloopReturn = new ArrayList<InetAddress>();
             int replicas_ = dcReplicationFactor.get(dc);
             List tokens = dcMap.get(dc);
             boolean bOtherRack = false;
             boolean doneDataCenterItr;
             int index = Collections.binarySearch(tokens, searchToken);
             if (index < 0)
             {
                 index = (index + 1) * (-1);
                 if (index >= tokens.size())
                 {
                     index = 0;
                 }
             }
             int totalNodes = tokens.size();
             // Add the node at the index by default
             InetAddress primaryHost = tokenToEndPointMap.get(tokens.get(index));
             forloopReturn.add(primaryHost);
             foundCount++;
             if (replicas_ == 1)
             {
                 continue;
             }
 
             int startIndex = (index + 1) % totalNodes;
             for (int i = startIndex, count = 1; count < totalNodes && foundCount < replicas_; ++count, i = (i + 1) % totalNodes)
             {
                 InetAddress endPointOfIntrest = tokenToEndPointMap.get(tokens.get(i));
                 if ((replicas_ - 1) > foundCount)
                 {
                     forloopReturn.add(endPointOfIntrest);
                     continue;
                 }
                 else
                 {
                     doneDataCenterItr = true;
                 }
                 // Now try to find one on a different rack
                 if (!bOtherRack)
                 {
                     if (!endPointSnitch.isOnSameRack(primaryHost, endPointOfIntrest))
                     {
                         forloopReturn.add(tokenToEndPointMap.get(tokens.get(i)));
                         bOtherRack = true;
                         foundCount++;
                     }
                 }
                 // If both already found exit loop.
                 if (doneDataCenterItr && bOtherRack)
                 {
                     break;
                 }
             }
 
             /*
             * If we found N number of nodes we are good. This loop wil just
             * exit. Otherwise just loop through the list and add until we
             * have N nodes.
             */
             for (int i = startIndex, count = 1; count < totalNodes && foundCount < replicas_; ++count, i = (i + 1) % totalNodes)
             {
                 if (!forloopReturn.contains(tokenToEndPointMap.get(tokens.get(i))))
                 {
                     forloopReturn.add(tokenToEndPointMap.get(tokens.get(i)));
                     foundCount++;
                 }
             }
             endpoints.addAll(forloopReturn);
         }
 
         return endpoints;
     }
 
     /**
      * This method will generate the QRH object and returns. If the Consistency
      * level is DCQUORUM then it will return a DCQRH with a map of local rep
      * factor alone. If the consistency level is DCQUORUMSYNC then it will
      * return a DCQRH with a map of all the DC rep facor.
      */
     @Override
     public <T> QuorumResponseHandler<T> getResponseHandler(IResponseResolver<T> responseResolver, int blockFor, int consistency_level)
     throws InvalidRequestException
     {
         if (consistency_level == ConsistencyLevel.DCQUORUM)
         {
             List<InetAddress> endpoints = getLocalEndPoints();
            return new DatacenterQuorumResponseHandler<T>(endpoints, locQFactor, responseResolver);
         }
         else if (consistency_level == ConsistencyLevel.DCQUORUMSYNC)
         {
             return new DatacenterQuorumSyncResponseHandler<T>(getQuorumRepFactor(), responseResolver);
         }
         return super.getResponseHandler(responseResolver, blockFor, consistency_level);
     }
 }
