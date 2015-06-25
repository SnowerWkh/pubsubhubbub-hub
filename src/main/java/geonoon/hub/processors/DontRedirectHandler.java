package geonoon.hub.processors;

import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectHandler;
import org.apache.http.protocol.HttpContext;

public class DontRedirectHandler implements RedirectHandler {

  //  @Override
    public URI getLocationURI(HttpResponse response, HttpContext context) throws ProtocolException {
        return null;
    }

   // @Override
    public boolean isRedirectRequested(HttpResponse response, HttpContext context) {
        return false;
    }

}
