package add.features.detector.repairpatterns;

import org.junit.Assert;
import org.junit.Test;

import add.entities.RepairPatterns;
import add.main.Config;
import add.utils.TestUtils;

public class CopyPasteDetectorTest {

    @Test
    public void closure35() {
        Config config = TestUtils.setupConfig("closure_35");

        RepairPatternDetector detector = new RepairPatternDetector(config);
        RepairPatterns repairPatterns = detector.analyze();

        Assert.assertTrue(repairPatterns.getFeatureCounter("copyPaste") == 0);
    }

    @Test
    public void closure110() {
        Config config = TestUtils.setupConfig("closure_110");

        RepairPatternDetector detector = new RepairPatternDetector(config);
        RepairPatterns repairPatterns = detector.analyze();

        Assert.assertTrue(repairPatterns.getFeatureCounter("copyPaste") == 0);
    }

    @Test
    public void closure131() {
        Config config = TestUtils.setupConfig("closure_131");

        RepairPatternDetector detector = new RepairPatternDetector(config);
        RepairPatterns repairPatterns = detector.analyze();

        Assert.assertTrue(repairPatterns.getFeatureCounter("copyPaste") > 0);
    }

    @Test
    public void lang8() {
        Config config = TestUtils.setupConfig("lang_8");

        RepairPatternDetector detector = new RepairPatternDetector(config);
        RepairPatterns repairPatterns = detector.analyze();

        Assert.assertTrue(repairPatterns.getFeatureCounter("copyPaste") == 0);
    }
}
