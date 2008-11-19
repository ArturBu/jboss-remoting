package org.jboss.remoting.metadata;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.BeanMetaDataFactory;
import org.jboss.remoting.core.util.CollectionUtil;
import org.jboss.xb.annotations.JBossXmlSchema;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * Metadata which describes a Remoting deployment.  This class is intended for the parsing of
 * {@code jboss-remoting.xml} descriptors.
 */
@JBossXmlSchema (namespace = "urn:jboss:remoting:3.0", elementFormDefault = XmlNsForm.QUALIFIED)
@XmlRootElement (name = "remoting")
@XmlType (name = "remoting")
public class RemotingMetaData implements BeanMetaDataFactory, Serializable {
    private static final long serialVersionUID = 1L;

    private List<EndpointMetaData> endpoints;

    /**
     * Get the list of nested endpoints as metadata.
     *
     * @return the endpoint metadata list
     */
    public List<EndpointMetaData> getEndpoints() {
        return endpoints;
    }

    /**
     * Set the list of nested endpoint metadata.
     *
     * @param endpoints the endpoint metadata list
     */
    @XmlElement(name = "endpoint")
    public void setEndpoints(final List<EndpointMetaData> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * Create a duplicate of this object.
     *
     * @return a duplicate of this object
     */
    public RemotingMetaData clone() throws CloneNotSupportedException {
        final RemotingMetaData metaData = (RemotingMetaData) super.clone();
        metaData.endpoints = new ArrayList<EndpointMetaData>();
        for (EndpointMetaData endpointMetaData : endpoints) {
            metaData.endpoints.add(endpointMetaData.clone());
        }
        return metaData;
    }

    /**
     * Get the metadata items that this deployment produces.
     *
     * @return the list of metadata items
     */
    @XmlTransient
    public List<BeanMetaData> getBeans() {
        final List<BeanMetaData> metaDataList = CollectionUtil.arrayList();
        for (EndpointMetaData endpointMetaData : endpoints) {
            metaDataList.addAll(endpointMetaData.getBeans());
        }
        return Collections.unmodifiableList(metaDataList);
    }
}
