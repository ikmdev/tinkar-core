package org.hl7.tinkar.integration.coordinate;

import org.hl7.tinkar.coordinate.Coordinates;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

class CoordinatesTest {

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
        System.out.println("Fast test");
    }

    @Test(groups = { "slow" })
    public void aSlowTest() {
        System.out.println("Slow test");
    }

}
