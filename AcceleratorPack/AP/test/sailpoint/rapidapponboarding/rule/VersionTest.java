package sailpoint.rapidapponboarding.rule;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.junit.Assert;
import sailpoint.rapidonboarding.Version;
import sailpoint.rapidonboarding.VersionConstants;


public class VersionTest {

    // Tests if the VersionConstants.class has values injected into it. We can't really rely on the native
    // OS to tell us this version information, even though this is likely running within the same
    // build activity in which the class was derived. In fact, I can't even rely on the derived
    // compilation unit to exist. So there's no verification that the values are accurate, only
    // that they exist
    
    private static final String KEY_VERSION = "key";
    private static final String KEY_PATCH_LEVEL = "patchlevel";
    private static final String KEY_REVISION = "revision";
    private static final String KEY_REPO_LOCATION = "repolocation";
    private static final String KEY_BUILDER = "builder";
    private static final String KEY_BUILDTIME = "buildtime";
    private static final String KEY_BUILDHOST = "buildhost";
    
    // These template values are only testable until a dev comes in later and changes them
    // to something else. Probably a good idea to drop a note in the .tmpl file pointing
    // them to this test.
    private static final String TMPL_VERSION = "@VERSION@";
    private static final String TMPL_PATCH_LEVEL = "@PATCH_LEVEL@";
    private static final String TMPL_REVISION = "@REVISION@";
    private static final String TMPL_REPO_LOCATION = "@REPO_LOCATION@";
    private static final String TMPL_BUILDER = "@BUILDER@";
    private static final String TMPL_BUILDTIME = "@BUILDTIME@";
    private static final String TMPL_BUILDHOST = "@BUILDHOST@";
    
    private Map<String, String> tmplValuesMap;
    
    public VersionTest() {
        tmplValuesMap = new HashMap<String, String>();
        tmplValuesMap.put(KEY_BUILDHOST, TMPL_BUILDHOST);
        tmplValuesMap.put(KEY_VERSION, TMPL_VERSION);
        tmplValuesMap.put(KEY_PATCH_LEVEL, TMPL_PATCH_LEVEL);
        tmplValuesMap.put(KEY_REVISION, TMPL_REVISION);
        tmplValuesMap.put(KEY_REPO_LOCATION, TMPL_REPO_LOCATION);
        tmplValuesMap.put(KEY_BUILDER, TMPL_BUILDER);
        tmplValuesMap.put(KEY_BUILDTIME, TMPL_BUILDTIME);
    }

    @Test
    public void testVersionValues() {
        // Ensure VersionConstants exists and has non-template values
        // If VersionConstants wasn't even generated, this will throw a ClassNotFoundException
        // instead. For a unit test, that's fine
        Map<String, String> constants = getVersionConstants();
        assert constants.keySet().equals(tmplValuesMap.keySet());
        for (String key : constants.keySet()) {
            // tests that the template values are not found in VersionConstants.class
            String constValue = constants.get(key);
            String tmpValue = tmplValuesMap.get(key);
            Assert.assertTrue("Template value " + tmpValue + " found for key: " + key, !constValue.equals(tmpValue));
        }
    }
    
    private Map<String, String> getVersionConstants() {
        Map<String, String> values = new HashMap<String, String>();
        values.put(KEY_VERSION, VersionConstants.VERSION);
        values.put(KEY_PATCH_LEVEL, VersionConstants.PATCH_LEVEL);
        values.put(KEY_REVISION, VersionConstants.REVISION);
        values.put(KEY_REPO_LOCATION, VersionConstants.REPO_LOCATION);
        values.put(KEY_BUILDER, VersionConstants.BUILDER);
        values.put(KEY_BUILDTIME, VersionConstants.BUILDTIME);
        values.put(KEY_BUILDHOST, VersionConstants.BUILDHOST);
        return values;
    }
    
    @Test
    public void testVersionClass() {
        // Part one is that the VersionConstants is an accessible class with non-template values
        // Part two is that Version is leveraging VersionConstants. This is the "part two" test
        Assert.assertTrue(Version.getBuilder().equals(VersionConstants.BUILDER));
        Assert.assertTrue(Version.getBuildHost().equals(VersionConstants.BUILDHOST));
        Assert.assertTrue(Version.getPatchLevel().equals(VersionConstants.PATCH_LEVEL));
        Assert.assertTrue(Version.getRepoLocation().equals(VersionConstants.REPO_LOCATION));
        Assert.assertTrue(Version.getRevision().equals(VersionConstants.REVISION));
        Assert.assertTrue(Version.getVersion().equals(VersionConstants.VERSION));
        Assert.assertTrue(Version.getBuildDate().equals(VersionConstants.BUILDTIME));
        
    }

}
