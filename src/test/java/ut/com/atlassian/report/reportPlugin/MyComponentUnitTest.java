package ut.com.atlassian.report.reportPlugin;

import org.junit.Test;
import com.atlassian.report.reportPlugin.api.MyPluginComponent;
import com.atlassian.report.reportPlugin.impl.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}