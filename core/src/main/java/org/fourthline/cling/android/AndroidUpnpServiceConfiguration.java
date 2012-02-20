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

package org.fourthline.cling.android;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import org.fourthline.cling.DefaultUpnpServiceConfiguration;
import org.fourthline.cling.binding.xml.DeviceDescriptorBinder;
import org.fourthline.cling.binding.xml.ServiceDescriptorBinder;
import org.fourthline.cling.binding.xml.UDA10DeviceDescriptorBinderSAXImpl;
import org.fourthline.cling.binding.xml.UDA10ServiceDescriptorBinderSAXImpl;
import org.fourthline.cling.transport.impl.apache.StreamClientConfigurationImpl;
import org.fourthline.cling.transport.impl.apache.StreamClientImpl;
import org.fourthline.cling.transport.impl.apache.StreamServerConfigurationImpl;
import org.fourthline.cling.transport.impl.apache.StreamServerImpl;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;
import org.fourthline.cling.transport.spi.StreamClient;
import org.fourthline.cling.transport.spi.StreamServer;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import org.fourthline.cling.transport.impl.GENAEventProcessorImpl;
import org.fourthline.cling.transport.impl.SOAPActionProcessorImpl;
import org.fourthline.cling.transport.spi.GENAEventProcessor;
import org.fourthline.cling.transport.spi.SOAPActionProcessor;


import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Configuration settings for deployment on Android.
 * <p>
 * This configuration utilizes the Apache HTTP Components transport implementation
 * found in {@link org.fourthline.cling.transport.impl.apache} for TCP/HTTP networking. It
 * will attempt to bind only to the WiFi network interface and addresses on an
 * Android device.
 * </p>
 * <p>
 * This configuration utilizes the SAX default descriptor binders found in
 * {@link org.fourthline.cling.binding.xml}. The system property <code>org.xml.sax.driver</code>
 * is set to <code>org.xmlpull.v1.sax2.Driver</code>.
 * </p>
 * <p>
 * The thread <code>Executor</code> is a <code>ThreadPoolExecutor</code> with the following
 * properties, optimized for machines with limited resources:
 * </p>
 * <ul>
 * <li>Core pool size of minimum 8 idle threads</li>
 * <li>Maximum 16 threads active</li>
 * <li>5 seconds keep-alive time before an idle thread is removed from the pool</li>
 * <li>A FIFO queue of maximum 512 tasks waiting for a thread from the pool</li>
 * </ul>
 * <p>
 * A warning message will be logged when all threads of the pool have been exhausted
 * and executions have to be dropped.
 * </p>
 *
 * @author Christian Bauer
 */
public class AndroidUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {

    final private static Logger log = Logger.getLogger(AndroidUpnpServiceConfiguration.class.getName());

    final protected Object manager;

    public AndroidUpnpServiceConfiguration(Object manager) {
        this(manager, 0); // Ephemeral port
    }

    public AndroidUpnpServiceConfiguration(Object manager, int streamListenPort) {
        super(streamListenPort, false);

        this.manager = manager;

        // This should be the default on Android 2.1 but it's not set by default
        System.setProperty("org.xml.sax.driver","org.xmlpull.v1.sax2.Driver");
    }

    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort) {
        return new AndroidNetworkAddressFactory(manager);
    }

    @Override
    public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new StreamServerImpl(
                new StreamServerConfigurationImpl(
                        networkAddressFactory.getStreamListenPort()
                )
        );
    }

    @Override
    public StreamClient createStreamClient() {
        return new StreamClientImpl(new StreamClientConfigurationImpl() {
        	public int getConnectionTimeoutSeconds() {
                return 2;
            }
        	public int getDataReadTimeoutSeconds() {
                return 3;
            }
        	public boolean getStaleCheckingEnabled() {
        		// comment from AndroidHttpClient.java:
        		//
                // Turn off stale checking.  Our connections break all the time anyway,
                // and it's not worth it to pay the penalty of checking every time.
        		return false;
        	}
        	public int getRequestRetryCount() {
        		// since "connections break all the time anyway", limit number of retries to
        		// minimize time spent in HttpClient.execute()
        		return 1;
        	}
        });
    }

    @Override
    protected DeviceDescriptorBinder createDeviceDescriptorBinderUDA10() {
        return new UDA10DeviceDescriptorBinderSAXImpl();
    }

    @Override
    protected ServiceDescriptorBinder createServiceDescriptorBinderUDA10() {
        return new UDA10ServiceDescriptorBinderSAXImpl();
    }

    @Override
    public int getRegistryMaintenanceIntervalMillis() {
        return 3000; // Preserve battery on Android, only run every 3 seconds
    }

    @Override
    protected Executor createDefaultExecutor() {
        return super.createDefaultExecutor();

        /* Old executor with limits, not really necessary...
        // Smaller pool and larger queue on Android, devices do not have much resources...
        ThreadPoolExecutor defaultExecutor = new ThreadPoolExecutor(8, 16, 5, TimeUnit.SECONDS, new ArrayBlockingQueue(512)) {
            @Override
            protected void beforeExecute(Thread thread, Runnable runnable) {
                super.beforeExecute(thread, runnable);
                thread.setName("Thread " + thread.getId() + " (Active: " + getActiveCount() + ")");
            }
        };

        defaultExecutor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.DiscardPolicy() {
                    @Override
                    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor) {

                        // Log and discard
                        log.warning(
                                "Thread pool saturated, discarding execution " +
                                "of '"+runnable.getClass()+"', consider raising the " +
                                "maximum pool or queue size"
                        );
                        super.rejectedExecution(runnable, threadPoolExecutor);
                    }
                }
        );

        return defaultExecutor;
        */
    }

    protected DocumentBuilderFactory createDocumentBuilderFactory() throws FactoryConfigurationError {
        if (Build.VERSION.SDK_INT >= 8) {
            try {
                Class clazz = Class.forName("net.sf.saxon.dom.DocumentBuilderFactoryImpl");
                return (DocumentBuilderFactory)clazz.newInstance();
            } catch (Throwable e) {
            }
        }

        return DocumentBuilderFactory.newInstance();
    }

    @Override
    protected SOAPActionProcessor createSOAPActionProcessor() {
        return new SOAPActionProcessorImpl() {
            @Override
            protected DocumentBuilderFactory createDocumentBuilderFactory() throws FactoryConfigurationError {
                return AndroidUpnpServiceConfiguration.this.createDocumentBuilderFactory();
            }
        };
    }

    @Override
    protected GENAEventProcessor createGENAEventProcessor() {
        return new GENAEventProcessorImpl() {
            @Override
            protected DocumentBuilderFactory createDocumentBuilderFactory() throws FactoryConfigurationError {
                return AndroidUpnpServiceConfiguration.this.createDocumentBuilderFactory();
            }
        };
    }
}
