package ut.com.atlassian.report;

import org.junit.Test;
import com.atlassian.report.api.MyPluginComponent;
import com.atlassian.report.impl.MyPluginComponentImpl;

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