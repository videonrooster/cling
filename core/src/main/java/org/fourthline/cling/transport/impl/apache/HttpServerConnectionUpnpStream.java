/*
 * Copyright (C) 2011 4th Line GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.fourthline.cling.transport.impl.apache;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.fourthline.cling.model.message.StreamRequestMessage;
import org.fourthline.cling.model.message.StreamResponseMessage;
import org.fourthline.cling.model.message.UpnpHeaders;
import org.fourthline.cling.model.message.UpnpMessage;
import org.fourthline.cling.model.message.UpnpOperation;
import org.fourthline.cling.model.message.UpnpRequest;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.transport.spi.UnsupportedDataException;
import org.fourthline.cling.transport.spi.UpnpStream;
import org.seamless.util.Exceptions;

import com.bubblesoft.org.apache.http.ConnectionClosedException;
import com.bubblesoft.org.apache.http.ConnectionReuseStrategy;
import com.bubblesoft.org.apache.http.HttpEntity;
import com.bubblesoft.org.apache.http.HttpEntityEnclosingRequest;
import com.bubblesoft.org.apache.http.HttpException;
import com.bubblesoft.org.apache.http.HttpRequest;
import com.bubblesoft.org.apache.http.HttpResponse;
import com.bubblesoft.org.apache.http.HttpResponseFactory;
import com.bubblesoft.org.apache.http.HttpServerConnection;
import com.bubblesoft.org.apache.http.HttpStatus;
import com.bubblesoft.org.apache.http.MethodNotSupportedException;
import com.bubblesoft.org.apache.http.ProtocolVersion;
import com.bubblesoft.org.apache.http.entity.ByteArrayEntity;
import com.bubblesoft.org.apache.http.entity.InputStreamEntity;
import com.bubblesoft.org.apache.http.entity.StringEntity;
import com.bubblesoft.org.apache.http.impl.DefaultConnectionReuseStrategy;
import com.bubblesoft.org.apache.http.impl.DefaultHttpResponseFactory;
import com.bubblesoft.org.apache.http.impl.DefaultHttpServerConnection;
import com.bubblesoft.org.apache.http.message.BasicStatusLine;
import com.bubblesoft.org.apache.http.params.BasicHttpParams;
import com.bubblesoft.org.apache.http.params.DefaultedHttpParams;
import com.bubblesoft.org.apache.http.params.HttpParams;
import com.bubblesoft.org.apache.http.protocol.BasicHttpContext;
import com.bubblesoft.org.apache.http.protocol.BasicHttpProcessor;
import com.bubblesoft.org.apache.http.protocol.HttpContext;
import com.bubblesoft.org.apache.http.protocol.HttpProcessor;
import com.bubblesoft.org.apache.http.protocol.HttpService;
import com.bubblesoft.org.apache.http.protocol.ResponseConnControl;
import com.bubblesoft.org.apache.http.protocol.ResponseContent;
import com.bubblesoft.org.apache.http.protocol.ResponseDate;
import com.bubblesoft.org.apache.http.util.EntityUtils;

/**
 * Implementation for Apache HTTP Components API.
 *
 * @author Christian Bauer
 */
public class HttpServerConnectionUpnpStream extends UpnpStream {

    final private static Logger log = Logger.getLogger(UpnpStream.class.getName());

    protected final HttpServerConnection connection;
    protected final BasicHttpProcessor httpProcessor = new BasicHttpProcessor();
    protected final HttpService httpService;
    protected final HttpParams params;

    protected HttpServerConnectionUpnpStream(ProtocolFactory protocolFactory,
                                             HttpServerConnection connection,
                                             final HttpParams params) {
        super(protocolFactory);
        this.connection = connection;
        this.params = params;

        // The Date header is recommended in UDA, need to document the requirement in StreamServer interface?
        httpProcessor.addInterceptor(new ResponseDate());

        // The Server header is only required for Control so callers have to add it to UPnPMessage
        // httpProcessor.addInterceptor(new ResponseServer());

        httpProcessor.addInterceptor(new ResponseContent());
        httpProcessor.addInterceptor(new ResponseConnControl());

        httpService =
                new UpnpHttpService(
                        httpProcessor,
                        new DefaultConnectionReuseStrategy(),
                        new DefaultHttpResponseFactory()
                );
        httpService.setParams(params);
    }

    public HttpServerConnection getConnection() {
        return connection;
    }

    public void run() {

        try {
            while (!Thread.interrupted() && connection.isOpen()) {
                log.fine("Handling request on open connection...");
                HttpContext context = new BasicHttpContext(null);
                httpService.handleRequest(connection, context);
            }
        } catch (ConnectionClosedException ex) {
            log.fine("Client closed connection");
            responseException(ex);
        } catch (SocketTimeoutException ex) {
            log.fine("Server-side closed socket (this is 'normal' behavior of Apache HTTP Core!): " + ex.getMessage());
        } catch (IOException ex) {
            log.warning("I/O exception during HTTP request processing: " + ex.getMessage());
            responseException(ex);
        } catch (HttpException ex) {
            throw new UnsupportedDataException("Request malformed: " + ex.getMessage(), ex);
        } catch (OutOfMemoryError ex) {
            log.fine("HttpServerConnectionUpnpStream Out of memory exception");
        } finally {
            try {
                connection.shutdown();
            } catch (IOException ex) {
                log.fine("Error closing connection: " + ex.getMessage());
            }
        }
    }

    /**
     * A thread-safe custom service implementation that creates a UPnP message from the request,
     * then passes it to <tt>UpnpStream#process()</tt>, finally sends the response back to the
     * client.
     */
    protected class UpnpHttpService extends HttpService {

        public UpnpHttpService(HttpProcessor processor, ConnectionReuseStrategy reuse, HttpResponseFactory responseFactory) {
            super(processor, reuse, responseFactory);
        }

        @Override
        protected void doService(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext ctx)
                throws HttpException, IOException {

            log.fine("Processing HTTP request: " + httpRequest.getRequestLine().toString());

            // Extract what we need from the HTTP httpRequest
            String requestMethod = httpRequest.getRequestLine().getMethod();
            String requestURI = httpRequest.getRequestLine().getUri();

            StreamRequestMessage requestMessage;
            try {
            	requestMessage =
                    new StreamRequestMessage(
                            UpnpRequest.Method.getByHttpName(requestMethod),
                            URI.create(requestURI)
                    );
            } catch(IllegalArgumentException e) {
            	String msg = "Invalid request URI: " + requestURI + ": " + e.getMessage();
            	log.warning(msg);
                throw new HttpException(msg, e);
            }
            
            if (requestMessage.getOperation().getMethod().equals(UpnpRequest.Method.UNKNOWN)) {
                log.fine("Method not supported by UPnP stack: " + requestMethod);
                throw new MethodNotSupportedException("Method not supported: " + requestMethod);
            }

            log.fine("Created new request message: " + requestMessage);

            // HTTP version
            int requestHttpMinorVersion = httpRequest.getProtocolVersion().getMinor();
            requestMessage.getOperation().setHttpMinorVersion(requestHttpMinorVersion);

            // Headers
            requestMessage.setHeaders(new UpnpHeaders(HeaderUtil.get(httpRequest)));
            
            InetAddress localAddress = ((DefaultHttpServerConnection)connection).getLocalAddress();
            if(localAddress == null) {
            	log.warning("got HTTP request without Local Address"); 
            } else {
            	requestMessage.setLocalAddress(localAddress.getHostAddress());
            }

            // Body
            if (httpRequest instanceof HttpEntityEnclosingRequest) {
                log.fine("Request contains entity body, setting on UPnP message");
                
                HttpEntityEnclosingRequest entityEnclosingHttpRequest = (HttpEntityEnclosingRequest) httpRequest;
                
                HttpEntity entity = entityEnclosingHttpRequest.getEntity();

                if (requestMessage.isContentTypeMissingOrText()) {
                    log.fine("HTTP request message contains text entity");
                    requestMessage.setBody(UpnpMessage.BodyType.STRING, EntityUtils.toString(entity));
                } else {
                    log.fine("HTTP request message contains binary entity");
                    requestMessage.setBody(UpnpMessage.BodyType.BYTES, EntityUtils.toByteArray(entity));
                }
                

            } else {
                log.fine("Request did not contain entity body");
            }

            // Finally process it
            StreamResponseMessage responseMsg;
            try {
                responseMsg = process(requestMessage);
            } catch (RuntimeException ex) {
                log.fine("Exception occured during UPnP stream processing: " + ex);
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "Cause: " + Exceptions.unwrap(ex), Exceptions.unwrap(ex));
                }

                log.fine("Sending HTTP response: " + HttpStatus.SC_INTERNAL_SERVER_ERROR);
                httpResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

                responseException(ex);
                return;
            }

            if (responseMsg != null) {
                log.fine("Sending HTTP response message: " + responseMsg);

                // Status line
                httpResponse.setStatusLine(
                        new BasicStatusLine(
                                new ProtocolVersion("HTTP", 1, responseMsg.getOperation().getHttpMinorVersion()),
                                responseMsg.getOperation().getStatusCode(),
                                responseMsg.getOperation().getStatusMessage()
                        )
                );

                log.fine("Response status line: " + httpResponse.getStatusLine());

                // Headers
                httpResponse.setParams(getResponseParams(requestMessage.getOperation()));
                HeaderUtil.add(httpResponse, responseMsg.getHeaders());

                // Entity
                if (responseMsg.hasBody() && responseMsg.getBodyType().equals(UpnpMessage.BodyType.BYTES)) {
                    httpResponse.setEntity(new ByteArrayEntity(responseMsg.getBodyBytes()));
                } else if (responseMsg.hasBody() && responseMsg.getBodyType().equals(UpnpMessage.BodyType.STRING)) {
                    StringEntity responseEntity = new StringEntity(responseMsg.getBodyString(), "UTF-8");
                    httpResponse.setEntity(responseEntity);
                }  else if (responseMsg.hasBody() && responseMsg.getBodyType().equals(UpnpMessage.BodyType.STREAM)) {
                	log.info("serving stream, len: " + responseMsg.getContentLength());
                	httpResponse.setEntity(new InputStreamEntity(responseMsg.getInputStream(), responseMsg.getContentLength()));
                }

            } else {
                // If it's null, it's 404, everything else needs a proper httpResponse
                log.fine("Sending HTTP response: " + HttpStatus.SC_NOT_FOUND);
                httpResponse.setStatusCode(HttpStatus.SC_NOT_FOUND);
            }

            responseSent(responseMsg);
        }

        protected HttpParams getResponseParams(UpnpOperation operation) {
            HttpParams localParams = new BasicHttpParams();
            return new DefaultedHttpParams(localParams, params);
        }

    }

}