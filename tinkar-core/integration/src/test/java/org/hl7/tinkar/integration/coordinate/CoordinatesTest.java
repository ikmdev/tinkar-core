package org.hl7.tinkar.integration.coordinate;

import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.coordinate.Coordinates;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.util.logging.Logger;

class CoordinatesTest {
    private static Logger LOG = Logger.getLogger(CoordinatesTest.class.getName());

    @BeforeSuite
    public void setupSuite() {
        LOG.info("setupSuite: " + this.getClass().getSimpleName());
        LOG.info(ServiceProperties.jvmUuid());
    }

    private Coordinates coordinatesUnderTest;

    @BeforeMethod
     void setUp() {
        coordinatesUnderTest = new Coordinates();
    }

    @Test
    void aTest() {
        Assert.assertEquals(1+1, 2);
    }

    @Test(groups = { "fast" })
    public void aFastTest() {
        LOG.info("Fast test");
    }

    @Test(groups = { "slow" })
    public void aSlowTest() {
        LOG.info("Slow test");
    }

}
