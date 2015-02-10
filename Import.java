
 /* 
 * Licensed Materials - Property of IBM
 * IBM Cognos Products: SDK Support
 * (C) Copyright IBM Corp. 2003, 2013
 * US Government Users Restricted Rights - Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
*/

/*
 * Import.java
 * Description: Technote 1338960 - SDK Sample to export or import a deployment
 * Tested with:  IBM Cognos BI 10.2.0, IBM Java 1.6, Axis 1.4
*/


import com.cognos.developer.schemas.bibus._3.*;

import org.apache.axis.client.Stub;
import org.apache.axis.message.SOAPHeaderElement;
import javax.xml.namespace.QName;

//import com.cognos.developer.schemas.bibus._3.holders.*;
import org.apache.axis.AxisFault;

import java.io.*;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.*;

import javax.xml.rpc.ServiceException;
public class Import {
	
	private ContentManagerService_PortType cmService = null;
	private MonitorService_PortType monitorService = null;
	
	private DeploymentOptionArrayProp doap = new DeploymentOptionArrayProp();
	public  static String logFile = "Import.csv";
	private String arguments[] = {"-a", "-i", "-d", "-s", "-u", "-p", "-c", "-g"};
	private Vector<String> packages = new Vector<String>();
	
   	public static void main(String[] args) 
   	{
   		String impDeploymentSpec = new String();
   		String impDeploymentName = new String();
   		String password = null;
		String gateway = "http://localhost:9300/p2pd/servlet/dispatch";
   		String nameSpace= null;
   		String userID = null;
   		String pass = null;
   		int args_len = args.length ;
   		Import imp = new Import();
   		
   		if (args.length < 1)
   		{
   			//If archiveName is more than one word, put it in quotes, ex. "SDK Import"
   			imp.printUsage();
   		}
	    for (int i=0; i<args_len; i++)
	    {
	   		if (args[i].compareToIgnoreCase("-s") == 0)
	   			nameSpace = args[++i];
	   		else 
	   			if (args[i].compareToIgnoreCase("-i") == 0)
	   				password = args[++i];
	   			else
		   			if (args[i].compareToIgnoreCase("-u") == 0)
		   				userID = args[++i];
		   			else
		   				if (args[i].compareToIgnoreCase("-p") == 0)
		   					pass = args[++i];
		   				else
		   					if (args[i].compareToIgnoreCase("-a") == 0)
		   						impDeploymentSpec =  args[++i];	   
		   					else
			   					if (args[i].compareToIgnoreCase("-d") == 0)
			   						impDeploymentName =  args[++i];	   
			   					else
		   						if (args[i].compareToIgnoreCase("-g") == 0)
		   							gateway =  args[++i];	   	
		   						else
		   							if (args[i].compareToIgnoreCase("-c") == 0)
		   							{	
		   								while (++i < args.length && !imp.isArgument(args[i]))
		   									imp.addPackages(args[i]);
		   								i--;
		   							}
		   							else
		   								imp.displayHelp();
	   }
	   imp.connectToReportServer(gateway);  
	   
	   //Don't try to login if using Anonymous
	   if (nameSpace != null && userID != null && pass != null)
	   	  imp.quickLogon(nameSpace, userID, pass);	
	   else
	   	  System.out.println("No logon information. Attempting to login as Anonymous user...");
	 	   
	 	if (impDeploymentSpec != null) 
	 	{	
		   String impPath = imp.insertNewImport(impDeploymentSpec, password, impDeploymentName);
		   if (impPath == null)
			  System.out.println("Problems occured while creating archive in CM " + impDeploymentSpec);
		   
		   String imported = imp.importPackage(impDeploymentSpec, password, impDeploymentName) ;
		   if (imported == null)
 	   	      System.out.println("Possible problems importing archive \n" + 
 	   	    		  impDeploymentSpec + "no eventId  - check the Import history in Cognos Connection.");
		   else
		   {
			System.out.println("Event was sucessful: eventID=" + imported);  
		   	imp.displayImportHistory(impDeploymentSpec, impDeploymentName);
		   }
	 	}
	 	else
	 		System.out.println("No Valid Archive Name Provided.");
   }
   	  	
   	//This method gets and sets all deploymentOptions from the archive with provided archive password
   	//In this script the deployment options are not changed.
   	public DeploymentOption[] deploymentOptions(String pPackage, String pPassword)
   	{
   		DeploymentOption[] opt = new DeploymentOption[1];
   		DeploymentOptionArrayProp options = new DeploymentOptionArrayProp(null,opt);
   		DeploymentOptionString archiveEncryptPassword = new DeploymentOptionString();
   		archiveEncryptPassword.setValue("<credential><password>" + pPassword + "</password></credential>");
   		archiveEncryptPassword.setName(DeploymentOptionEnum.fromString("archiveEncryptPassword"));
   		opt[0] = archiveEncryptPassword;

   		options.setValue(opt);
   		try
   		{
   			// setup the deployment options.
   			opt = (DeploymentOption[])getDO(pPackage, options);
   			opt = takeOwnership(opt);
   			DeploymentOption optNew[] = new DeploymentOption[opt.length + 1];
   			for (int i=0; i< opt.length; i++ )
   				optNew[i] = opt[i];
   			
   			optNew[opt.length] = archiveEncryptPassword;
   			return optNew;
   		}
   		catch (Exception e)
   		{
   			printErrorMessage(e);
   			return opt;
   		}
   	}
   	
   	//This method gets and sets all deploymentOptions from the archive without a password
   	//In this script the deployment options are not changed.
   	public DeploymentOption[] deploymentOptions(String p_archive)
   	{
   		try 
   		{
	   		Option dOptions[] = new Option [] {};
	   		dOptions = cmService.getDeploymentOptions(p_archive, dOptions);
	   		DeploymentOption[] opt = new DeploymentOption[dOptions.length + 1] ;
	   		DeploymentOptionAuditLevel recordingLevel = new DeploymentOptionAuditLevel();
   	   		recordingLevel.setName(DeploymentOptionEnum.fromString("recordingLevel"));
   	   		recordingLevel.setValue(AuditLevelEnum.full);
	   		opt[0] = recordingLevel;
	   		
	   		System.arraycopy(dOptions, 0, opt, 1, dOptions.length);
	
	   		opt = takeOwnership(opt);
	   		
	   		
			if (packages != null && packages.size() > 0)
			{
					for (int i=1; i<opt.length; i++)
					if (opt[i].getName().getValue().compareToIgnoreCase(DeploymentOptionEnum.fromString("package").getValue()) == 0)
					{
						int j = packages.size();
						if (packages != null && j>0)
						{	
							PackageDeploymentInfo pDeplInfo[] = new  PackageDeploymentInfo[j];
							DeploymentOptionPackageInfo pack = new DeploymentOptionPackageInfo();
												
							for (int k=0; k<j; k++)
							{	
								MultilingualToken mt[] = new MultilingualToken[1];
								mt[0] = new  MultilingualToken();
								mt[0].setLocale("en");
								mt[0].setValue(packages.get(k).toString());
								pDeplInfo[k] = new PackageDeploymentInfo();
								pDeplInfo[k].setSourceName(mt);
								pDeplInfo[k].setTargetName(mt);
								pDeplInfo[k].setEnabled(true);  //? Should it be set to true ?
							}
							
							//will not change the target name. Keep by default
							pack.setName(DeploymentOptionEnum.fromString("package"));
							pack.setValue(pDeplInfo);
							opt[i] = pack;
						}
					}	
			}
			return opt;
		} 
   		catch (RemoteException e) 
		{
			e.printStackTrace();
			return null;
		}
   	}
   	
   	private Option[] getDO(String p_archive, DeploymentOptionArrayProp p_opt)
   	{
   		try
   		{
   			Option opt[] = new Option[]{};
   			opt = p_opt.getValue();
   			
   			opt = cmService.getDeploymentOptions(p_archive, opt);
	   		DeploymentOption[] optNew = new DeploymentOption[opt.length + 1] ;
	   		DeploymentOptionAuditLevel recordingLevel = new DeploymentOptionAuditLevel();
   	   		recordingLevel.setName(DeploymentOptionEnum.fromString("recordingLevel"));
   	   		recordingLevel.setValue(AuditLevelEnum.full);
   	   		optNew[0] = recordingLevel;
	   		
	   		System.arraycopy(opt, 0, optNew, 1, opt.length);
   			return optNew ;
   		}
   		catch (Exception e)
   		{
   			printErrorMessage(e);
   			return null;
   		}
   	}
   	
   	public DeploymentOption[] takeOwnership(DeploymentOption[] opt)
   	{
   		for (int i=0; i< opt.length; i++ )
   			if (opt[i].getName() == DeploymentOptionEnum.fromString("takeOwnership"))
   			{
   				//Change the ownership to the user performing the import. Otherwise  
   				//there will be errors
   				((DeploymentOptionBoolean )opt[i]).setValue(true);
   			}
   			return opt;
   	}
   	
   	public void displayImportHistory(String name, String impDeploymentName)
   	{
   		PropEnum props[] = new PropEnum []{PropEnum.defaultName, 
   				PropEnum.searchPath, PropEnum.deployedObjectStatus,
   				PropEnum.objectClass, PropEnum.status, PropEnum.hasMessage,
				PropEnum.deployedObjectClass};

   		String impPath="/adminFolder/importDeployment[@name='"+(impDeploymentName.length() > 0 ? impDeploymentName : name)+"']"  + "//history//*";

   		String msg = "Import started on " +  Calendar.getInstance().getTime().toString() +"\n";
	   	msg += "Importing \"" + name +  "\"";
	   		
  		SearchPathMultipleObject spMulti = new  SearchPathMultipleObject(impPath);
	   	try
	   	{
	   		BaseClass bc[]=cmService.query(spMulti, props, new Sort[] {}, new QueryOptions());
	   		if (bc != null && bc.length > 0)
				for (int i=0; i< bc.length; i++)
				{
		   			if (bc[i].getObjectClass().getValue() == ClassEnum.fromString("deploymentDetail"))
		   			{
		   				DeploymentDetail dd = (DeploymentDetail)bc[i];
		   				//Print messages if any 
		   				if (dd.getMessage() != null)
		   					System.out.println(dd.getMessage().getValue());
		   				if (dd.getDeployedObjectStatus().getValue() != null)
		   					msg += "\n" + dd.getDeployedObjectClass().getValue() + "," + dd.getDefaultName().getValue() + "," +
		   								dd.getDeployedObjectStatus().getValue().getValue();
		   			}
		   		}
	   			writeOutputToFile (logFile, msg);
	   		}
	   		catch (Exception e)
	   		{
	   			printErrorMessage(e);
	   		}
   	}

   	//Import the archive
	public String importPackage(String name, String password, String importDeploymentName) 
	{		
		String impPath="/adminFolder/importDeployment[@name='"+(importDeploymentName.length() > 0 ? importDeploymentName : name)+"']";
		String eventID = null;
		try
		{
			//The timeout has to be increased when the system is running out of resources
			org.apache.axis.client.Stub s = (org.apache.axis.client.Stub) cmService;
			s.setTimeout(0);  // set to not timeout
			
			System.out.println("Importing archive " + name + "... Please wait...");
	
			SearchPathSingleObject spSingle = new SearchPathSingleObject(impPath);
			// See DCF 1375609 for details regarding this change
			AsynchReply reply = monitorService.run(spSingle, new ParameterValue[]{}, new Option[] {});
			if (!reply.getStatus().equals(AsynchReplyStatusEnum.conversationComplete)) 	
			{
			    while (!reply.getStatus().equals(AsynchReplyStatusEnum.conversationComplete)) 
			    {
			    	reply = getMonitorService().wait(reply.getPrimaryRequest(), new ParameterValue[]{},new Option[] {});
			    }
			}
			if (reply.getStatus().getValue().equals("conversationComplete") || 
					reply.getStatus().getValue().equals("complete") )
				 eventID = ((AsynchDetailEventID)reply.getDetails()[0]).getEventID();
		}
		catch (Exception e)
		{
			printErrorMessage(e);
		}
		return eventID;
	}
	
	//handle service requests that do not specify new conversation for backwards compatibility
	public MonitorService_PortType getMonitorService() {
		
		return getMonitorService(false, "");
		
	}
	
	public MonitorService_PortType getMonitorService(boolean isNewConversation, String RSGroup)
	{
		BiBusHeader bibus = null;
		bibus =
			getHeaderObject(((Stub)monitorService).getResponseHeader("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader"), isNewConversation, RSGroup);
		
		if (bibus == null) 
		{
			BiBusHeader CMbibus = null;
			CMbibus =
				getHeaderObject(((Stub)cmService).getResponseHeader("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader"), true, RSGroup);
			
			((Stub)monitorService).setHeader("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader", CMbibus);
		}
		else
		{
			((Stub)monitorService).clearHeaders();
			((Stub)monitorService).setHeader("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader", bibus);
			
		}
		return monitorService;
	}
	
	//Use this method when copying headers, such as for requests to services
	public static BiBusHeader getHeaderObject(SOAPHeaderElement SourceHeader, boolean isNewConversation, String RSGroup)
	{
		final String BIBUS_NS = "http://developer.cognos.com/schemas/bibus/3/";
		final String BIBUS_HDR = "biBusHeader";
		final QName BUS_QNAME = new QName(BIBUS_NS, BIBUS_HDR);
		
		if (SourceHeader == null)
			return null;
		
		BiBusHeader bibus = null;
		try {
			bibus = (BiBusHeader)SourceHeader.getValueAsType(BUS_QNAME);
			// Note BUS_QNAME expands to:
			// new QName("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader")
			
			//If the header will be used for a new conversation, clear
			//tracking information, and set routing if supplied (clear if not)
			if (isNewConversation){
				
				bibus.setTracking(null);
				
				//If a Routing Server Group is specified, direct requests to it
				if (RSGroup.length()>0) {
					RoutingInfo routing = new RoutingInfo(RSGroup);
					bibus.setRouting(routing);
				}
				else {
					bibus.setRouting(null);
				}
			}
		} catch (Exception e) {
			
			e.printStackTrace();
		}
		
		return bibus;
	}
	
	//This method selects and inserts an archive in CM from all available .zip files
	//in <cognos_install>\deployment if using default configuration
	public String insertNewImport(String importName, String password, String importDeploymentName)
	{
		try 
		{
			SearchPathMultipleObject spMulti = new SearchPathMultipleObject();
			spMulti.set_value("/adminFolder");
			
			PropEnum props[] = new PropEnum[]{ PropEnum.searchPath,PropEnum.policies };
			BaseClass importDeplFolder[] = cmService.query(spMulti,props, new Sort[]{}, new QueryOptions());
			
			ImportDeployment newImport = new ImportDeployment();
			
			TokenProp tp = new TokenProp();
			tp.setValue(importDeploymentName.length() > 0 ? importDeploymentName : importName);
			newImport.setDefaultName(tp);
			
			BaseClassArrayProp parent = new BaseClassArrayProp();
			parent.setValue(importDeplFolder);
			newImport.setParent(parent);
		
			if (password != null && password.length() >= 1)
				doap.setValue(deploymentOptions(importName, password));
			else
				doap.setValue(deploymentOptions(importName));
				
			newImport.setDeploymentOptions(doap);
					
			String importPath = "/adminFolder";

			AddOptions ao = new AddOptions();
			ao.setUpdateAction(UpdateActionEnum.replace); //replace if already exists
			
			SearchPathSingleObject spSingle = new SearchPathSingleObject(importPath);
			
			BaseClass archive[] = cmService.add(spSingle,new BaseClass[]{newImport},ao);
			if (archive == null || archive.length <= 0)
				System.out.println("No Import was added to the Content Store.");
			
			 return archive[0].getSearchPath().getValue();
		}
		catch( Exception ex) 
		{
			printErrorMessage(ex);
			return null;
		}
	}
	
	//Extracts the error message from the stack trace
	private void printErrorMessage(Exception e)
	{
		AxisFault f = (AxisFault)e;
		String a1 = f.dumpToString();
		int start = a1.indexOf("<messageString>");
		int end = a1.indexOf("</messageString>");
		if (start < end)
		{	
			String message = a1.substring(start+15,end-1);
			System.out.println(message);
		}
		else
			System.out.println(e.getMessage());
	}
	
	//Write the status of each object to a file
	private void writeOutputToFile (String logFile, String msg)
	{	
		try
		{
			FileOutputStream fos = new FileOutputStream(logFile);
			Writer w = 
			new BufferedWriter(new OutputStreamWriter(fos));
			w.write(msg);
			w.flush();
			w.close();  
			System.out.println("The Import is complete. The details have been saved to a file " + logFile);
		}
		catch (IOException e)
		{
			System.out.println(e);
		}
	}
	
	//Checks if a string is one of the arguments
	private boolean isArgument(String p_argument)
	{
		for (int i=0; i<arguments.length; i++)
		{
			if (p_argument.equals(arguments[i]))
				return true;
		}
		return false;
	}
	
	//Add to packages to be imported
	public void addPackages(String p_package)
	{
		packages.add(p_package);
	}
	
	//Print usage of the script
	public void printUsage()
	{
		String usage = "\njava Import -a <archiveName> [-i <archivePassword>] [-d deploymentName] [-c package1 package2 ...] [-s <namespaceID> -u <userID> -p <userPassword>] [-g <CognosBIDispatcher>]";
		String example = "Example: \njava Import -a CMarchive -i password -d CMarchive_import -s \"LDAPID\" -u \"User\" -p \"UserPassword\" -g http://server:9300/p2pd/servlet/dispatch";
		
		System.out.println(usage);
		System.out.println(example);
		displayHelp();
		System.exit(1);
	}
	
	//Displays help
	public void displayHelp()
	{
		String usage = "";
		usage += "Import the contents of a Deployment archive.\n\n";
		usage += "Usage:\n\n";
		usage += "-a archiveName\n\tThe name of the new archive\n";
		usage += "-i archivePassword\n\tThe password for the archive. Mandatory for Content Store import.\n";
		usage += "-d deploymentName\n\tThe name of the new import deployment (optional. Can be used to resolve naming conflicts with existing export deployments)\n";
		usage += "-c package1 [package2]\n\tPackages to be imported\n";
		usage += "-s namespaceID\n\tNamespaceID the user belongs to.\n";
		usage += "-u userID\n\tUserID of a System Administrator.\n";
		usage += "-p userPassword\n\tPassword for the UserID.\n";
		usage += "-g CognosBIDispatcher\n\tDispatcher URL for Cognos BI.\n";
		usage += "\t Default: http://localhost:9300/p2pd/servlet/dispatch\n";
		
		System.out.println(usage);
		System.exit(1);
	}
	
    //This method logs the user to Cognos BI
	public String quickLogon(String namespace, String uid, String pwd)
	{
		StringBuffer credentialXML = new StringBuffer();

		credentialXML.append("<credential>");
		credentialXML.append("<namespace>").append(namespace).append("</namespace>");
		credentialXML.append("<username>").append(uid).append("</username>");
		credentialXML.append("<password>").append(pwd).append("</password>");
		credentialXML.append("</credential>");

		String encodedCredentials = credentialXML.toString();
	    XmlEncodedXML xmlCredentials = new XmlEncodedXML();
	    xmlCredentials.set_value(encodedCredentials);
	        
	   //Invoke the ContentManager service logon() method passing the credential string
	   //You will pass an empty string in the second argument. Optionally,
	   //you could pass the Role as an argument 

	   try
		{		
			cmService.logon(xmlCredentials,null );

			SOAPHeaderElement temp = ((Stub)cmService).getResponseHeader 
				("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader");
			BiBusHeader cmBiBusHeader = (BiBusHeader)temp.getValueAsType
				(new QName ("http://developer.cognos.com/schemas/bibus/3/","biBusHeader"));
			((Stub)cmService).setHeader
				("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader", cmBiBusHeader);
			((Stub)monitorService).setHeader
				("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader", cmBiBusHeader);

		}
		catch (Exception e)
		{
			System.out.println(e);
		}

		return ("Logon successful as " + uid);
	}
	
	//This method connects to Cognos BI
	public void connectToReportServer (String endPoint)
	{		
		ContentManagerService_ServiceLocator cmServiceLocator = new ContentManagerService_ServiceLocator();
		MonitorService_ServiceLocator  moServiceLocator = new MonitorService_ServiceLocator();
	    try 
	    {
			cmService = cmServiceLocator.getcontentManagerService(new java.net.URL(endPoint));
			monitorService = moServiceLocator.getmonitorService(new java.net.URL(endPoint));
//			 set the Axis request timeout

			((Stub) cmService).setTimeout(0); // in milliseconds, 0 turns the timeout off
			((Stub)monitorService).setTimeout(0); // in milliseconds, 0 turns the timeout off

		} 
	    catch (MalformedURLException e) 
		{
			e.printStackTrace();
		} 
		catch (ServiceException e) 
		{
			e.printStackTrace();
		}
	}
}

