package geonoon.hub.processors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeedDistributor {

    private final static Logger LOG = LoggerFactory.getLogger(FeedDistributor.class);
    
    private LinkedBlockingQueue<String> distributionQueue;
    private final ExecutorService executor;
    private File feedDirectory;
    
    private ConcurrentHashMap<String,Set<String>> subfeedMap;
    
//    private DefaultHttpClient httpclient = new DefaultHttpClient();
    //private DefaultHttpClient httpclient=null ;
    private DefaultHttpClient httpclient = new DefaultHttpClient();
   
    
    public FeedDistributor(LinkedBlockingQueue<String> distributionQueue, 
    		File feedDirectory,ConcurrentHashMap<String,Set<String>> subfeedMap) {
        this.distributionQueue = distributionQueue;
        this.feedDirectory = feedDirectory;
        this.subfeedMap=subfeedMap;
        executor = Executors.newFixedThreadPool(2);
        
        //not allowed to redirect when distributing
        httpclient.setRedirectHandler(new DontRedirectHandler());
        
    }

    private Set<String> getSubscribers(String feed) {
    	//need  implemented by self
        return subfeedMap.get(feed);
    }
    
    public void start() {
        LOG.info("Starting FeedDistributor");
        executor.execute(new Runnable() {
        //    @Override
            public void run() {
                while(true) {
                    try {
                        String feed = distributionQueue.take();
                        
                        Set<String> subscribers = getSubscribers(feed);
                        
                        String fileName = DigestUtils.md5Hex(feed);
                        File file = new File(feedDirectory, fileName);
                        
                        if(!subscribers.isEmpty()) {
                            
                            byte[] buffer = new byte[(int)file.length()];
                            
                            LOG.debug("Distributing " + feed);
                            FileInputStream fis = null;
                            try {
                                 fis = new FileInputStream(file);
                                fis.read(buffer, 0, buffer.length);

                                for(String subscriber : subscribers) {
                                    HttpPost post = new HttpPost(subscriber);
                                    
                                    // TODO might also use FileEntity, but that will not cache the data
                                    post.setEntity(new ByteArrayEntity(buffer));
                                    HttpResponse response = null;
                                    try {
                                        response = httpclient.execute(post);
                                        if(response.getStatusLine().getStatusCode() == 200) {
                                            LOG.debug("Succeeded distributing to subscriber {}", subscriber);
                                        } else {
                                            LOG.debug("Failed distributing to subscriber \"{}\" with error \"{}\"", subscriber, response.getStatusLine());
                                        }
                                    } catch(IOException e) {
                                        LOG.debug("Failed distributing to subscriber: " + subscriber, e);
                                    } finally {
                                        if(response != null) {
                                            if(response.getEntity() != null) {
                                                InputStream in = response.getEntity().getContent();
                                                if(in != null) in.close();
                                            }
                                        }
//                                        httpclient.getConnectionManager().shutdown();
                                    }
                                    // TODO handle retries
                                }
                                LOG.info("Feed distributed to {} subscribers: {}", subscribers.size(), feed);
                            } catch (IOException e) {
                                LOG.debug("Failed to distribute feed: " + feed, e);
                            }
                            finally{
                            	if(fis != null){
                            		try {
										fis.close();
										file.delete();
										
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
                            		
                            	}
                            }
                        } else {
                            LOG.debug("No subscribers for published feed, ignoring: {}", feed);
                            file.delete();
                        }
                    } catch (InterruptedException e) {
                        // shutting down
                        break;
                    }
                }
                
                LOG.info("Stopped FeedDistributor");
            }
        });
    }

    public void stop() {
        LOG.info("Stopping FeedDistributor");
        executor.shutdownNow();
    }

}
