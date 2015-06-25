package geonoon.hub.processors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * @version 2015-06-25
 * @author Kehuai
 *
 */
public class FeedRetriever {

    private final static Logger LOG = LoggerFactory.getLogger(FeedRetriever.class);
    
    private final LinkedBlockingQueue<String> retrieveQueue;
    private final LinkedBlockingQueue<String> distributionQueue; 
    private final ExecutorService executor;
    private File feedDirectory;
    private File tmpDirectory = new File(System.getProperty("java.io.tmpdir"));
    
    private HttpClient httpclient = new DefaultHttpClient();
    //private HttpClient httpclient =null;
    
    public FeedRetriever(LinkedBlockingQueue<String> retrieveQueue, LinkedBlockingQueue<String> distributionQueue, 
    		File feedDirectory) {
        this.retrieveQueue = retrieveQueue;
        this.distributionQueue = distributionQueue;
        this.feedDirectory = feedDirectory;
        
        executor = Executors.newFixedThreadPool(2);
    }

    public void start() {
        LOG.info("Starting FeedRetriever");
        executor.execute(new Runnable() {
     //       @Override
            public void run() {
                while(true) {
                    try {
                        String feed = retrieveQueue.take();
                        
                        String fileName = DigestUtils.md5Hex(feed);
                        
                        LOG.debug("Downloading feed {}", feed);
                        
                        HttpGet httpget = new HttpGet(feed);
                        try {
                        	
                            HttpResponse response = httpclient.execute(httpget);
                            HttpEntity entity = response.getEntity();
                            
                            File destFile = new File(feedDirectory, fileName);
                            File tmpFile = new File(tmpDirectory, fileName);
                            FileOutputStream tmpFOS = new FileOutputStream(tmpFile);
                            entity.writeTo(tmpFOS);
                            tmpFOS.flush();
                            tmpFOS.close();
                            
                            LOG.debug("Feed downloaded: {}", feed);
                            //something is wrong
                            if(tmpFile.renameTo(destFile)) {
                                LOG.warn("Feed successfully retrived, putting for distribution: {}", feed);
                                distributionQueue.put(feed);
                            } else {
                                LOG.warn("Failed to move tmp file \"{}\" to destination \"{}\"", tmpFile.getAbsolutePath(), destFile.getAbsolutePath());
                            }
                            
                        } catch (ClientProtocolException e) {
                            LOG.error("Failed to download feed: " + feed, e);
                        } catch (IOException e) {
                            LOG.error("Failed to download feed: " + feed, e);
                        }
                        finally{
//                        	httpclient.getConnectionManager().shutdown();
                        }
                    } catch (InterruptedException e) {
                        // shutting down
                        break;
                    }
                }
                
                LOG.info("Stopped FeedRetriever");
            }
        });
    }

    public void stop() {
        LOG.info("Stopping FeedRetriever");
        executor.shutdownNow();
    }

}
