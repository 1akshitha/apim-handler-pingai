/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.apimgt.securityenforcer.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Component;
import org.wso2.carbon.apimgt.securityenforcer.dto.AISecurityHandlerConfig;
import org.wso2.carbon.apimgt.securityenforcer.publisher.HttpDataPublisher;
import org.wso2.carbon.apimgt.securityenforcer.publisher.Publisher;
import org.wso2.carbon.apimgt.securityenforcer.publisher.async.AsyncPublisher;
import org.wso2.carbon.apimgt.securityenforcer.publisher.async.AsyncPublisherThreadPool;
import org.wso2.carbon.apimgt.securityenforcer.publisher.hybrid.HybridPublisher;
import org.wso2.carbon.apimgt.securityenforcer.publisher.sync.SyncPublisher;
import org.wso2.carbon.apimgt.securityenforcer.publisher.sync.SyncPublisherThreadPool;
import org.wso2.carbon.apimgt.securityenforcer.utils.AISecurityException;
import org.wso2.carbon.apimgt.securityenforcer.utils.AISecurityHandlerConstants;
import org.wso2.carbon.apimgt.securityenforcer.utils.SecurityHandlerConfiguration;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;
import java.io.IOException;

@Component(name = "org.wso2.carbon.apimgt.securityenforcer", immediate = true)
public class PingAIHandlerComponent implements BundleActivator {

    private static final Log log = LogFactory.getLog(PingAIHandlerComponent.class);

    private String operationMode;
    private HttpDataPublisher httpDataPublisher;

    public void start(BundleContext bundleContext) throws Exception {
        log.info("OSGi start method for Ping AI security handler");

        AISecurityHandlerConfig securityHandlerConfig = getConfigData();
        operationMode = securityHandlerConfig.getMode();
        ServiceReferenceHolder.getInstance().setSecurityHandlerConfig(securityHandlerConfig);

        Publisher requestPublisher;
        Publisher responsePublisher;

        switch (operationMode) {
        case AISecurityHandlerConstants.SYNC_MODE_STRING:
            requestPublisher = new SyncPublisher();
            break;
        case AISecurityHandlerConstants.ASYNC_MODE_STRING:
            requestPublisher = new AsyncPublisher();
            break;
        case AISecurityHandlerConstants.HYBRID_MODE_STRING:
            requestPublisher = new HybridPublisher();
            break;
        default:
            throw new Exception("Operation mode is incorrect for Ping AI Security Handler");
        }

        ServiceReferenceHolder.getInstance().setRequestPublisher(requestPublisher);

        //response publisher is for the second sideband request with the backend response metadata. This is sent
        //asynchronously in all the operation methods and for that async publisher instance is needed. As both async and
        //hybrid modes contains async publisher instance, only for the sync mode, there will be a new additional instance
        //created.
        if (AISecurityHandlerConstants.SYNC_MODE_STRING.equals(operationMode)) {
            responsePublisher = new AsyncPublisher();
        } else {
            responsePublisher = requestPublisher;
        }

        ServiceReferenceHolder.getInstance().setResponsePublisher(responsePublisher);

        AISecurityHandlerConfig.AseConfig aseConfiguration = securityHandlerConfig.getAseConfig();
        AISecurityHandlerConfig.DataPublisherConfig dataPublisherConfiguration = securityHandlerConfig
                .getDataPublisherConfig();
        AISecurityHandlerConfig.ProxyConfig proxyConfiguration = securityHandlerConfig.getProxyConfig();

        try {
            httpDataPublisher = new HttpDataPublisher(aseConfiguration, dataPublisherConfiguration, proxyConfiguration);
        } catch (AISecurityException e) {
            log.error("Error when creating a httpDataPublisher Instance " + e.getMessage());
            throw new Exception(e);
        }

        ServiceReferenceHolder.getInstance().setHttpDataPublisher(httpDataPublisher);
    }

    public void stop(BundleContext bundleContext) {
        log.info("OSGi stop method for Ping AI Security Handler");
        if (AISecurityHandlerConstants.ASYNC_MODE_STRING.equals(operationMode)) {
            AsyncPublisherThreadPool.getInstance().cleanup();
            log.info("Cleaning the Async thread pool");
        } else {
            AsyncPublisherThreadPool.getInstance().cleanup();
            SyncPublisherThreadPool.getInstance().cleanup();
            log.info("Cleaning both Async and sync thread pools");
        }

        try {
            httpDataPublisher.getHttpClient().close();
            log.info("Closing the Http Client");
        } catch (IOException e) {
            log.error("Error when closing the HttpClient");
        }

    }

    /**
     * This method will read the config file.
     */
    private AISecurityHandlerConfig getConfigData() throws AISecurityException {
        SecurityHandlerConfiguration configuration = new SecurityHandlerConfiguration();
        configuration.load(CarbonUtils.getCarbonConfigDirPath() + File.separator
                + AISecurityHandlerConstants.CONFIG_FILE_NAME);
        return configuration.getPingAISecurityHandlerProperties();
    }
}
