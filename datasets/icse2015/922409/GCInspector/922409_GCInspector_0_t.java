 package org.apache.cassandra.service;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
 import java.util.*;
 import java.util.concurrent.ExecutorService;
 import org.apache.cassandra.concurrent.JMXEnabledThreadPoolExecutor;
 import org.apache.cassandra.utils.WrappedRunnable;
 
 import com.sun.management.GarbageCollectorMXBean;
 import com.sun.management.GcInfo;
 import java.lang.management.MemoryUsage;
 import java.lang.management.ManagementFactory;
 import javax.management.MBeanServer;
 import javax.management.ObjectName;
 
 public class GCInspector
 {
     public static final GCInspector instance = new GCInspector();
 
    private static final Logger logger = LoggerFactory.getLogger(GCInspector.class);
     final static long INTERVAL_IN_MS = 10 * 1000;
     final static long MIN_DURATION = 200;
 
     private HashMap<String, Long> gctimes = new HashMap<String, Long>();
 
     List<GarbageCollectorMXBean> beans = new ArrayList<GarbageCollectorMXBean>();
 
     public GCInspector()
     {
         MBeanServer server = ManagementFactory.getPlatformMBeanServer();
         try
         {
             ObjectName gcName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
             for (ObjectName name : server.queryNames(gcName, null))
             {
                 GarbageCollectorMXBean gc = ManagementFactory.newPlatformMXBeanProxy(server, name.getCanonicalName(), GarbageCollectorMXBean.class);
                 beans.add(gc);
             }
         }
         catch (Exception e)
         {
             throw new RuntimeException(e);
         }
     }
 
     public void start()
     {
         TimerTask t = new TimerTask()
         {
             public void run()
             {
                 logIntervalGCStats();
             }
         };
         new Timer("GC inspection").schedule(t, INTERVAL_IN_MS, INTERVAL_IN_MS);
     }
 
     private void logIntervalGCStats()
     {
         for (GarbageCollectorMXBean gc : beans)
         {
             GcInfo gci = gc.getLastGcInfo();
             if (gci == null)
                 continue;
 
             Long previous = gctimes.get(gc.getName());
             if (previous != null && previous == gc.getCollectionTime())
                 continue;
             gctimes.put(gc.getName(), gc.getCollectionTime());
 
             long previousMemoryUsed = 0;
             long memoryUsed = 0;
             long memoryMax = 0;
             for (Map.Entry<String, MemoryUsage> entry : gci.getMemoryUsageBeforeGc().entrySet())
             {
                 previousMemoryUsed += entry.getValue().getUsed();
             }
             for (Map.Entry<String, MemoryUsage> entry : gci.getMemoryUsageAfterGc().entrySet())
             {
                 MemoryUsage mu = entry.getValue();
                 memoryUsed += mu.getUsed();
                 memoryMax += mu.getMax();
             }
 
             String st = String.format("GC for %s: %s ms, %s reclaimed leaving %s used; max is %s",
                                       gc.getName(), gci.getDuration(), previousMemoryUsed - memoryUsed, memoryUsed, memoryMax);
             if (gci.getDuration() > MIN_DURATION)
                 logger.info(st);
             else if (logger.isDebugEnabled())
                 logger.debug(st);
         }
     }
 }
