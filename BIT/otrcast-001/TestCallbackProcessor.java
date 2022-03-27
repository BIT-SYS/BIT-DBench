package de.kisner.otrcast.controller.processor.hotfolder;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.configuration.Configuration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.kisner.otrcast.controller.processor.hotfolder.CallbackProcessor;
import de.kisner.otrcast.interfaces.processor.CallbackInterface;
import de.kisner.otrcast.test.AbstractOtrcastTest;
import de.kisner.otrcast.test.OtrCastUtilTestBootstrap;

public class TestCallbackProcessor extends AbstractOtrcastTest implements CallbackInterface
{
	
	final static Logger logger = LoggerFactory.getLogger(TestCallbackProcessor.class);
	
	private File fHot;
	private File fCallback;
	
	@Before
	public void before()
	{
		fHot = new File(fTarget,"hotfolder");
		if (!fHot.exists()){fHot.mkdir();}
	}
	
	public TestCallbackProcessor()
	{
		
	}
	
	@Test
	public void test() throws Exception
	{
		final CallbackProcessor cbp = new CallbackProcessor(this);
		
		DefaultCamelContext context = new DefaultCamelContext();
		context.addRoutes(new RouteBuilder() {
		    public void configure()
		    {	
		    	from(fHot.toURI().toString()).process(cbp);
		    }
		});
		context.start();
		File f = new File(fHot,"test");f.createNewFile();
		Thread.sleep(2000);
		Assert.assertEquals(f.getAbsolutePath(), fCallback.getAbsolutePath());
	}
	
	@Test @Ignore
	public void test2()
	{
		logger.error("Target is: "+fTarget.getAbsolutePath());
	}
	
	@SuppressWarnings("unused")
	public static void main(String[] args) throws Exception
	{
		Configuration config = OtrCastUtilTestBootstrap.init();
		TestCallbackProcessor.initTargetDirectory();
		TestCallbackProcessor test = new TestCallbackProcessor();
		
		test.before();
		test.test();
		test.test2();
	}

	@Override
	public void processing(File f)
	{
		logger.debug(f.getAbsolutePath());
		this.fCallback=f;
	}
}