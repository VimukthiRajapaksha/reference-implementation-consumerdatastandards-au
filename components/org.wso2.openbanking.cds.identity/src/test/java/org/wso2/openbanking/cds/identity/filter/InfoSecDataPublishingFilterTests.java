/**
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.openbanking.cds.identity.filter;

import com.wso2.openbanking.accelerator.common.config.OpenBankingConfigParser;
import com.wso2.openbanking.accelerator.common.exception.OpenBankingException;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.openbanking.cds.common.config.OpenBankingCDSConfigParser;
import org.wso2.openbanking.cds.common.data.publisher.CDSDataPublishingService;
import org.wso2.openbanking.cds.common.utils.CommonConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.FilterChain;

import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.wso2.openbanking.cds.identity.filter.util.TestConstants.EXTERNAL_TRAFFIC_HEADER;

/**
 * Test class for CDS Infosec Data Publishing Filter.
 */
@PowerMockIgnore("jdk.internal.reflect.*")
@PrepareForTest({OpenBankingCDSConfigParser.class, OpenBankingConfigParser.class, CDSDataPublishingService.class})
public class InfoSecDataPublishingFilterTests extends PowerMockTestCase {

    private OpenBankingCDSConfigParser openBankingCDSConfigParserMock;
    private OpenBankingConfigParser openBankingConfigParserMock;

    MockHttpServletRequest request;
    MockHttpServletResponse response;
    FilterChain filterChain;
    InfoSecDataPublishingFilter filter;
    Map<String, Object> cdsConfigs = new HashMap<>();
    Map<String, Object> configs = new HashMap<>();

    @BeforeClass
    public void init() throws OpenBankingException {

        cdsConfigs.put(CommonConstants.EXTERNAL_TRAFFIC_HEADER_NAME, EXTERNAL_TRAFFIC_HEADER);
        cdsConfigs.put(CommonConstants.EXTERNAL_TRAFFIC_EXPECTED_VALUE, "true");

        openBankingCDSConfigParserMock = PowerMockito.mock(OpenBankingCDSConfigParser.class);
        PowerMockito.mockStatic(OpenBankingCDSConfigParser.class);
        PowerMockito.when(OpenBankingCDSConfigParser.getInstance()).thenReturn(openBankingCDSConfigParserMock);
        PowerMockito.when(openBankingCDSConfigParserMock.getConfiguration()).thenReturn(cdsConfigs);

        filter = Mockito.spy(InfoSecDataPublishingFilter.class);
    }

    @BeforeMethod
    public void beforeMethod() {

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = Mockito.spy(FilterChain.class);

        configs.put("DataPublishing.Enabled", "true");
        openBankingConfigParserMock = PowerMockito.mock(OpenBankingConfigParser.class);
        PowerMockito.mockStatic(OpenBankingConfigParser.class);
        PowerMockito.when(OpenBankingConfigParser.getInstance()).thenReturn(openBankingConfigParserMock);
        PowerMockito.when(openBankingConfigParserMock.getConfiguration()).thenReturn(configs);
    }

    @Test(description = "Test the attributes in the latency data map")
    public void latencyDataMapAttributesTest() {

        String messageId = UUID.randomUUID().toString();
        request.setAttribute("REQUEST_IN_TIME", System.currentTimeMillis());
        Map<String, Object> latencyData = filter.generateLatencyDataMap(request, messageId);
        assertEquals(latencyData.get("correlationId"), messageId);
        assertNotNull(latencyData.get("requestTimestamp"));
        assertNotNull(latencyData.get("backendLatency"));
        assertNotNull(latencyData.get("requestMediationLatency"));
        assertNotNull(latencyData.get("responseLatency"));
        assertNotNull(latencyData.get("responseMediationLatency"));
    }

    @Test(description = "Test the ResponseLatency attribute in the latency data map")
    public void latencyDataMapNegativeResponseLatencyTest() {

        String messageId = UUID.randomUUID().toString();
        request.setAttribute("REQUEST_IN_TIME", System.currentTimeMillis() + (60 * 1000));
        Map<String, Object> latencyData = filter.generateLatencyDataMap(request, messageId);
        assertEquals(latencyData.get("responseLatency"), 0L);
    }

    @Test(description = "Test that data is not published when X-External-Traffic header contains expected value and " +
            "data publishing is disabled")
    public void testDataNotPublishedWhenExternalTrafficHeaderPresentAndDataPublishingDisabled() throws Exception {

        CDSDataPublishingService cdsDataPublishingServiceMock = PowerMockito.mock(CDSDataPublishingService.class);
        PowerMockito.mockStatic(CDSDataPublishingService.class);
        PowerMockito.when(CDSDataPublishingService.getCDSDataPublishingService()).thenReturn(
                cdsDataPublishingServiceMock);

        Mockito.doReturn(new HashMap<>()).when(filter).generateInvocationDataMap(Mockito.any(), Mockito.any(),
                Mockito.any());
        Mockito.doReturn(new HashMap<>()).when(filter).generateLatencyDataMap(Mockito.any(), Mockito.any());

        request.addHeader(EXTERNAL_TRAFFIC_HEADER, "true");
        configs.put("DataPublishing.Enabled", "false");

        filter.doFilter(request, response, filterChain);

        // Verify that data is NOT published
        verify(cdsDataPublishingServiceMock, Mockito.never()).publishApiInvocationData(Mockito.anyMap());
        verify(cdsDataPublishingServiceMock, Mockito.never()).publishApiLatencyData(Mockito.anyMap());
    }

    @Test(description = "Test that data is published when X-External-Traffic header contains expected value")
    public void testDataPublishedWhenExternalTrafficHeaderPresentAndContainExpectedValue() throws Exception {

        CDSDataPublishingService cdsDataPublishingServiceMock = PowerMockito.mock(CDSDataPublishingService.class);
        PowerMockito.mockStatic(CDSDataPublishingService.class);
        PowerMockito.when(CDSDataPublishingService.getCDSDataPublishingService()).thenReturn(
                cdsDataPublishingServiceMock);

        Mockito.doReturn(new HashMap<>()).when(filter).generateInvocationDataMap(Mockito.any(), Mockito.any(),
                Mockito.any());
        Mockito.doReturn(new HashMap<>()).when(filter).generateLatencyDataMap(Mockito.any(), Mockito.any());

        request.addHeader(EXTERNAL_TRAFFIC_HEADER, "true");

        filter.doFilter(request, response, filterChain);

        // Verify that data is published
        verify(cdsDataPublishingServiceMock).publishApiInvocationData(Mockito.anyMap());
        verify(cdsDataPublishingServiceMock).publishApiLatencyData(Mockito.anyMap());
    }

    @Test(description = "Test that data is not published when X-External-Traffic header contains an unexpected value")
    public void testDataPublishedWhenExternalTrafficHeaderHasUnexpectedValue() throws Exception {

        CDSDataPublishingService cdsDataPublishingServiceMock = PowerMockito.mock(CDSDataPublishingService.class);
        PowerMockito.mockStatic(CDSDataPublishingService.class);
        PowerMockito.when(CDSDataPublishingService.getCDSDataPublishingService()).thenReturn(
                cdsDataPublishingServiceMock);

        Mockito.doReturn(new HashMap<>()).when(filter).generateInvocationDataMap(Mockito.any(), Mockito.any(),
                Mockito.any());
        Mockito.doReturn(new HashMap<>()).when(filter).generateLatencyDataMap(Mockito.any(), Mockito.any());

        request.addHeader(EXTERNAL_TRAFFIC_HEADER, "false");

        filter.doFilter(request, response, filterChain);

        // Verify that data is NOT published
        verify(cdsDataPublishingServiceMock, Mockito.never()).publishApiInvocationData(Mockito.anyMap());
        verify(cdsDataPublishingServiceMock, Mockito.never()).publishApiLatencyData(Mockito.anyMap());
    }

    @Test(description = "Test that data is not published when X-External-Traffic header absent")
    public void testDataNotPublishedWhenExternalTrafficHeaderAbsent() throws Exception {

        CDSDataPublishingService cdsDataPublishingServiceMock = PowerMockito.mock(CDSDataPublishingService.class);
        PowerMockito.mockStatic(CDSDataPublishingService.class);
        PowerMockito.when(CDSDataPublishingService.getCDSDataPublishingService()).thenReturn(
                cdsDataPublishingServiceMock);

        Mockito.doReturn(new HashMap<>()).when(filter).generateInvocationDataMap(Mockito.any(), Mockito.any(),
                Mockito.any());
        Mockito.doReturn(new HashMap<>()).when(filter).generateLatencyDataMap(Mockito.any(), Mockito.any());

        filter.doFilter(request, response, filterChain);

        // Verify that data is NOT published
        verify(cdsDataPublishingServiceMock, Mockito.never()).publishApiInvocationData(Mockito.anyMap());
        verify(cdsDataPublishingServiceMock, Mockito.never()).publishApiLatencyData(Mockito.anyMap());
    }

}
