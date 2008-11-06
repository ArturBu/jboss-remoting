/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting;

import org.jboss.remoting.spi.RequestHandlerSource;

/**
 * A listener for watching service registrations on an endpoint.
 */
public interface ServiceListener {

    /**
     * Receive notification that a service was registered.
     *
     * @param listenerHandle the handle to this listener
     * @param info the servce information
     */
    void serviceRegistered(SimpleCloseable listenerHandle, ServiceInfo info);

    /**
     * 
     */
    final class ServiceInfo {
        private String endpointName;
        private String serviceType;
        private String groupName;
        private boolean remote;
        private int metric;
        private RequestHandlerSource requestHandlerSource;
        private SimpleCloseable registrationHandle;

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(final String serviceType) {
            this.serviceType = serviceType;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(final String groupName) {
            this.groupName = groupName;
        }

        public int getMetric() {
            return metric;
        }

        public void setMetric(final int metric) {
            this.metric = metric;
        }

        public RequestHandlerSource getRequestHandlerSource() {
            return requestHandlerSource;
        }

        public void setRequestHandlerSource(final RequestHandlerSource requestHandlerSource) {
            this.requestHandlerSource = requestHandlerSource;
        }

        public SimpleCloseable getRegistrationHandle() {
            return registrationHandle;
        }

        public void setRegistrationHandle(final SimpleCloseable registrationHandle) {
            this.registrationHandle = registrationHandle;
        }

        public String getEndpointName() {
            return endpointName;
        }

        public void setEndpointName(final String endpointName) {
            this.endpointName = endpointName;
        }

        public boolean isRemote() {
            return remote;
        }

        public void setRemote(final boolean remote) {
            this.remote = remote;
        }
    }
}
