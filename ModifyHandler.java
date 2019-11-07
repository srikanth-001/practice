package uk.co.vodafone.asapgw.operationhandler;


import static uk.co.vodafone.asapgw.util.ASAPGWConstants.DUAL_MODIFY_RESP_DELAY;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.DUAL_SUBADMIN_MODIFY_RESP_DELAY;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.IMSI;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.RUNMODE;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.SECONDARY_PROV;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.SOAP_PROTOCOL;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.UID;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.USERNAME;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.VFCREDENTIAL;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.VFSID;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.VFSUBSCRIBER;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.VFSUBSERV;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.VFUSER;
import static uk.co.vodafone.asapgw.util.ASAPGWConstants.VFUSERSERVICE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import siemens.names.prov.gw.spml._2._0.ResultCode;
import uk.co.vodafone.asapgw.asap.ASAPMessageHandler;
import uk.co.vodafone.asapgw.dao.ldap.LDAPClient;
import uk.co.vodafone.asapgw.dao.ldap.exception.LDAPDAOException;
import uk.co.vodafone.asapgw.dao.soap.SoapClient;
import uk.co.vodafone.asapgw.dao.soap.SoapClientFactory;
import uk.co.vodafone.asapgw.dao.soap.exception.SOAPDAOException;
import uk.co.vodafone.asapgw.operationhandler.mapper.ldap.CurObjectMapper;
import uk.co.vodafone.asapgw.operationhandler.mapper.ldap.LDAPResponseMapper;
import uk.co.vodafone.asapgw.operationhandler.mapper.ldap.LdapResponse;
import uk.co.vodafone.asapgw.operationhandler.mapper.ldap.exception.LDAPMappingException;
import uk.co.vodafone.asapgw.operationhandler.mapper.ldap.exception.LDAPOperationFailureException;
import uk.co.vodafone.asapgw.operationhandler.mapper.spml.SPMLRequestMapper;
import uk.co.vodafone.asapgw.operationhandler.mapper.spml.SPMLResponseMapper;
import uk.co.vodafone.asapgw.operationhandler.mapper.spml.exception.SPMLMappingException;
import uk.co.vodafone.asapgw.operationhandler.mapper.spml.exception.SPMLOperationFailureException;
import uk.co.vodafone.asapgw.provapi.exception.ProvAPIException;
import uk.co.vodafone.asapgw.provapi.logging.AuditLogItem;
import uk.co.vodafone.asapgw.provapi.logging.AuditLogItemHelper;
import uk.co.vodafone.asapgw.provapi.logging.LogType;
import uk.co.vodafone.asapgw.provapi.logging.LoggerUtils;
import uk.co.vodafone.asapgw.provapi.xml.IdentifierModifyType;
import uk.co.vodafone.asapgw.provapi.xml.ModifyRequest;
import uk.co.vodafone.asapgw.provapi.xml.ModifyResponse;
import uk.co.vodafone.asapgw.provapi.xml.VfsubscriberType;
import uk.co.vodafone.asapgw.provapi.xml.VfukaacredentialType;
import uk.co.vodafone.asapgw.provapi.xml.VfuserType;
import uk.co.vodafone.asapgw.subadmin.eventfeed.ServicePropertiesManager;
import uk.co.vodafone.asapgw.subadmin.eventfeed.xml.SubscriberState;
import uk.co.vodafone.asapgw.util.ASAPGWConstants;
import uk.co.vodafone.asapgw.util.ASAPGWProperties;
import uk.co.vodafone.asapgw.util.ASAPGWStatusCodes;
import uk.co.vodafone.asapgw.util.ASAPGWUtils;
import uk.co.vodafone.asapgw.util.SubAdminUtils;
import uk.co.vodafone.asapgw.util.XMLConverter;
import uk.co.vodafone.cur.aa.AASubscriber;
import uk.co.vodafone.cur.aa.AASubscriberProfile;
import uk.co.vodafone.cur.aa.AAUser;
import uk.co.vodafone.cur.aa.AAUserProfile;
import uk.co.vodafone.cur.aa.Credential;
import uk.co.vodafone.cur.aa.CredentialManager;
import uk.co.vodafone.cur.core.CurManager;
import uk.co.vodafone.cur.core.Subscriber;
import uk.co.vodafone.cur.core.SubscriberManager;
import uk.co.vodafone.cur.core.Subscriber_Asapgw;
import uk.co.vodafone.cur.core.User;
import uk.co.vodafone.cur.core.UserManager;
import uk.co.vodafone.cur.core.User_AsaPgw;
import uk.co.vodafone.cur.exception.CurApiInternalError;
import uk.co.vodafone.cur.exception.CurCommunicationException;
import uk.co.vodafone.cur.exception.CurConfigurationException;
import uk.co.vodafone.cur.exception.CurException;
import uk.co.vodafone.cur.exception.CurInvalidOperationException;
import uk.co.vodafone.cur.exception.CurMethodFailureException;
import uk.co.vodafone.cur.exception.CurOperationNotSupportedException;
import uk.co.vodafone.cur.exception.CurOperationalLimitException;
import uk.co.vodafone.cur.exception.CurSchemaViolationException;
import uk.co.vodafone.cur.exception.CurSecurityException;
import uk.co.vodafone.cur.exception.CurSystemException;
import uk.co.vodafone.cur.exception.CurUnavailableException;
import uk.co.vodafone.vm.AttributeTableException;



@Service
/**
 * 
 * @author SinghS43
 * this to Handle ADD Request
 * 	- Performs respective Mapping to LDAP and SPML request to pass it to the downstream system
 *  - Send the Request to the downstream system
 *  - Maps response back to the one for user
 *  - Performs auditing
 */
public class ModifyHandler  {


	private static final Logger modLog = Logger.getLogger(ASAPGWConstants.LOG_MODIFY_PROVAPI);
	private static final String logClassName = ModifyHandler.class.getName();
	private static final String responseDelayDualMode = ASAPGWProperties.getPropertyValue(DUAL_MODIFY_RESP_DELAY);
	private static final String subAdminResDelayDualMode = ASAPGWProperties.getPropertyValue(DUAL_SUBADMIN_MODIFY_RESP_DELAY);
	
	@Autowired
	LDAPClient ldapClient;

	@Autowired
	SoapClientFactory soapClientFactory;

	@Autowired
	SPMLAsyncService asyncService;
	
	@Autowired
	XMLConverter xmlConverter;
	
	@Autowired
	SubAdminUtils subAdminUtils;
	
	@Autowired
	ASAPMessageHandler asapMessageHandler;

	/**
	 * 
	 * @param modifyRequest
	 * @return
	 * @throws ProvAPIException 
	 */
	public ModifyResponse processModifyDual (ModifyRequest modifyRequest) throws ProvAPIException {
		ModifyResponse primaryCurResponse = new ModifyResponse();
		LdapResponse ldapResp = new LdapResponse();
		siemens.names.prov.gw.spml._2._0.ModifyRequest spmlRequest = null;
		User user = null;
		ProvAPIException spmlMapException = null;
		LoggerUtils.log(LogType.MODIFY,Level.INFO, "ModifyHandler: modifying in primary CUR first",logClassName);
		try {
			String key = null;
			Subscriber subscr = null;
			IdentifierModifyType reqId = modifyRequest.getIdentifier();
			if (null == reqId) {
				LDAPOperationFailureException ex = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_IDENTIFIER, ex);
				ex.setErrorDetails("Identifier is empty");
				LoggerUtils.logException("ModifyHandler: Identifier is empty" , ex,logClassName);
				throw ex;
			} else if (StringUtils.isEmpty(reqId.getName()) || StringUtils.isEmpty(reqId.getValue())) {
				LDAPOperationFailureException excep = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_IDENTIFIER, excep);
				excep.setErrorDetails("Either Identifier Name or Value are missing in the request.");
				throw excep;
			} else if (reqId.getName().equalsIgnoreCase(UID)) {
				user = ldapClient.getUserMgr().get(reqId.getValue());
				key = UID;
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Retrieved user by uid"+reqId.getValue()+"",logClassName);
			} else if (reqId.getName().equalsIgnoreCase(USERNAME)) {
				user = ldapClient.getUserMgr().getByUsername(reqId.getValue());
				key =  USERNAME;
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Retrieved user by username"+reqId.getValue()+"",logClassName);
			} else if (reqId.getName().equalsIgnoreCase(VFSID)) {
				subscr = ldapClient.getSubscriberMgr().get(reqId.getValue());
				key = VFSID;
				if (subscr != null)
					user = ldapClient.getUserMgr().get(subscr.getUid());
				else {
					LDAPOperationFailureException ex = new LDAPOperationFailureException();
					ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, ex);
					ex.setErrorDetails("Subscriber not found for user: " + reqId.getValue());
					LoggerUtils.logException("ModifyHandler: Subscriber not found" , ex,logClassName);
					throw ex;
				}
				LoggerUtils.log(LogType.MODIFY,Level.INFO,"ModifyHandler: Retrieved user by MSISDN",logClassName);

			} else if (reqId.getName().equalsIgnoreCase(IMSI)) {
				LoggerUtils.log(LogType.MODIFY, Level.ERROR,"ModifyHandler: Modify attempted by IMSI in dual",logClassName);
				LDAPOperationFailureException excep = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_OP_NOT_SUPPORTED_DUAL_MODE, excep);
				LoggerUtils.logException("ModifyHandler: IMSI not supported" , excep,logClassName);
				throw excep;
			} else {
				LoggerUtils.log(LogType.MODIFY, Level.ERROR,"Invalid identifier in request",logClassName);
				LDAPOperationFailureException ex = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_IDENTIFIER, ex);
				ex.setErrorDetails("Invalid identifier specified "+ reqId.getValue());
				LoggerUtils.logException("ModifyHandler: Invalid identifier specified" , ex,logClassName);
				throw ex;
			}

			if(user == null) {
				LDAPOperationFailureException excep = new LDAPOperationFailureException();
				LoggerUtils.log(LogType.MODIFY,Level.ERROR, "ModifyHandler: User not found",logClassName);
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, excep);
				excep.setErrorDetails("There are no users for this identifier " + reqId.getValue());
				throw excep;
			}


			if ((null == modifyRequest.getVfuserDelete()) && (null == modifyRequest.getVfuserReplace())
					&& (null == modifyRequest.getVfuserAdd())) {
				LDAPOperationFailureException ex = new LDAPOperationFailureException();
				LoggerUtils.log(LogType.MODIFY,Level.ERROR, "ModifyHandler: Nothing to modify",logClassName);
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_NOTHING_TO_MODIFY, ex);
				ex.setErrorDetails("ModifyHandler: No user specified for update in modify request");
				LoggerUtils.logException("ModifyHandler: No user specified for update" , ex,logClassName);
				throw ex;
			}

			List<VfsubscriberType> inputSubscribers = new ArrayList<VfsubscriberType>();

			if(modifyRequest.getVfuserDelete() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserDelete().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserDelete().getVfsubscriber());
			}
			if(modifyRequest.getVfuserReplace() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserReplace().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserReplace().getVfsubscriber());
			}
			if(modifyRequest.getVfuserAdd() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserAdd().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserAdd().getVfsubscriber());
			}

			if(CollectionUtils.isNotEmpty(inputSubscribers)) {
				LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"total subscribers received in request " + inputSubscribers.size(),logClassName);
				Set subscribers = user.getSubscribers();
				if(CollectionUtils.isEmpty(subscribers)) {
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "There are no subscribers found in this user " +user.getId(),logClassName);
					LDAPOperationFailureException excep = new LDAPOperationFailureException();
					ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, excep);
					excep.setErrorDetails("There are no subscribers found in this user " +user.getId());
					throw excep;
				}

				else {
					for (VfsubscriberType input : inputSubscribers) {
						if (null == input.getVfsid() || StringUtils.isEmpty(input.getVfsid()) ){
							LoggerUtils.log(LogType.MODIFY, Level.ERROR,"ModifyHandler: vfsid not present in modify request",logClassName);
							LDAPOperationFailureException excep = new LDAPOperationFailureException();
							ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_MISSING_VFSID, excep);
							excep.setErrorDetails("ModifyHandler: vfsid not present in modify request");
							LoggerUtils.logException("ModifyHandler: vfsid not present in modify request", excep,logClassName);
							throw excep;
						}
						//if(StringUtils.isNotEmpty(input.getVfsid())) {
						else {
							boolean isExists = false;
							for (Object existing : subscribers) {
								Subscriber ldapsubscr = (Subscriber) existing;
								if(ldapsubscr.getMsisdn().equalsIgnoreCase(input.getVfsid())) {
									isExists = true;
									break;
								}
							}
							if(!isExists) {
								LDAPOperationFailureException excep = new LDAPOperationFailureException();
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Subscriber with vfsid[" +  input.getVfsid() +"] is not associated with the user ",logClassName);
								ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_UNRELATED_OBJECT_UPDATE_FAIL, excep);
								excep.setErrorDetails("Subscriber with vfsid[" +  input.getVfsid() +"] is not associated with the user ");
								throw excep;
							}
						}
					}
				}	
			}

			/*
			 *  prepare SPML Request for secondary cur - to handle AAServices
			 */
			spmlRequest = new siemens.names.prov.gw.spml._2._0.ModifyRequest();
			try {
				LoggerUtils.log(LogType.MODIFY,Level.INFO, "Mapping AsapgwModify request to SPMLModify request",logClassName);
				User_AsaPgw user1 = ldapClient.getCurMgr().getAsaPgwUser(key, reqId.getValue());
				SPMLRequestMapper.aspgwToSpml(LogType.MODIFY,modifyRequest, spmlRequest, user1);
			} catch (SPMLMappingException ex) {
				LoggerUtils.log(LogType.MODIFY, Level.ERROR,"ModifyHandler : spml mapping error : "+ExceptionUtils.getRootCauseMessage(ex),logClassName);
				spmlMapException = ex;
			}
			
			/* Perform Operation in Primary CUR first */
			if(StringUtils.isNotEmpty(reqId.getOperationPrirotiy())) {
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Checking for the operation priority:",logClassName);
				for(char op : reqId.getOperationPrirotiy().toLowerCase().toCharArray()) {
					switch(op) {
					case 'a' :
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Modify-add is requested",logClassName);
						doLdapModifyAdd(modifyRequest, user); 
						break;
					case 'r' :
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Modify-replace is requested",logClassName);
						doLdapModifyReplace(modifyRequest, user); 
						break;
					case 'd' :
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Modify-delete is requested",logClassName);
						doLdapModifyDelete(modifyRequest, user); 
						break;
					default: break;
					}
				}
			} else {

				if (null != modifyRequest.getVfuserDelete())
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Do modify-delete",logClassName);	
				doLdapModifyDelete(modifyRequest, user); 
				if (null != modifyRequest.getVfuserReplace())
					LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"Do modify-replace",logClassName);
				doLdapModifyReplace(modifyRequest, user); 
				if (null != modifyRequest.getVfuserAdd())
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Do modify-add",logClassName);
				doLdapModifyAdd(modifyRequest, user); 
			}

			//Updating user before setting the response object
			user =  ldapClient.getUserMgr().get(user.getId());
			ldapResp.setUser(user);
			if(CollectionUtils.isNotEmpty(user.getSubscribers())) {
				for(Object sub : user.getSubscribers()) {
					if(sub != null) {
						ldapResp.getSubscribers().add((Subscriber) sub);
					}
				}
			}
			
			if (modifyRequest.getReturn() != null) {
				ldapResp.setReturnScope(modifyRequest.getReturn().getResultingObject());
				if(CollectionUtils.isNotEmpty(modifyRequest.getReturn().getResultingObjectClass()))
					ldapResp.setReturnScopeObjectClass(modifyRequest.getReturn().getResultingObjectClass());
			}

			try {
				LoggerUtils.log(LogType.MODIFY,Level.INFO, "Mapping the response to Asa-PGW",logClassName);
				LDAPResponseMapper.ldapToAsaPgw(LogType.MODIFY,ldapResp, primaryCurResponse);
			} catch (Exception ex) {
				LoggerUtils.log(LogType.MODIFY,Level.ERROR,"LDAPMappingException while adding user :" , logClassName);
				LoggerUtils.logException("LDAPMappingException while adding user :" , ex,logClassName);
				LDAPMappingException excep = new LDAPMappingException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_RESP_MAPG_ERR, excep);
				excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
				throw excep;
			}

		} catch (LDAPMappingException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"LDAPMappingException while adding user" ,logClassName);
			LoggerUtils.logException("LDAPMappingException while adding user" , ex,logClassName);
			LDAPMappingException excep = new LDAPMappingException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_REQ_MAPG_ERR, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurInvalidOperationException|CurApiInternalError|CurOperationNotSupportedException|CurMethodFailureException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"Any of CurInvalidOperationException,CurApiInternalError,CurOperationNotSupportedException,CurMethodFailureException while modifying user " ,logClassName);
			LoggerUtils.logException("Any of CurInvalidOperationException,CurApiInternalError,CurOperationNotSupportedException,CurMethodFailureException while modifying user " , ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_OP_FAILURE, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurOperationalLimitException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"CurOperationalLimitException while modifying user : " ,logClassName);
			LoggerUtils.logException("CurOperationalLimitException while modifying user  " , ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_OP_FAILURE, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurSchemaViolationException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"CurSchemaViolationException while modifying user :" ,logClassName);
			LoggerUtils.logException("CurSchemaViolationException while modifying user :" , ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_SCHEMA, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurCommunicationException|CurConfigurationException|CurSecurityException|CurUnavailableException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.FATAL,"Any of CurCommunicationException,CurConfigurationException,CurSecurityException,CurUnavailableException  in  :" ,logClassName);
			LoggerUtils.logException("Any of CurCommunicationException,CurConfigurationException,CurSecurityException,CurUnavailableException  in :" , ex,logClassName);
			LDAPDAOException excep = new LDAPDAOException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurSystemException|CurException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.FATAL,"CurException while modifying user :",logClassName);
			LoggerUtils.logException("CurException while modifying user :", ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		}
		LoggerUtils.log(LogType.MODIFY, Level.INFO,"ModifyHandler: finished modify in Primary CUR",logClassName);	

		if (ASAPGWConstants.SUCCESS_CODE.equals(primaryCurResponse.getResultStatus().getResultCode())) {

			if(ASAPGWProperties.getPropertyValue(SECONDARY_PROV).equalsIgnoreCase("seq")) {
				processModifyDualInNewCur(modifyRequest, spmlRequest);

			} else if (ASAPGWProperties.getPropertyValue(SECONDARY_PROV).equalsIgnoreCase("async")) {
				// start the asynchronous task for the secondary cur
				asyncService.doAsynchronousSpmlModify(modifyRequest, spmlRequest, user.getId(), spmlMapException);
			}
			
			try {
				Thread.sleep(Integer.parseInt(responseDelayDualMode));
			} catch (NumberFormatException | InterruptedException e) {
				LoggerUtils.log(LogType.MODIFY,Level.INFO,"Exception occured while trying to delay the resonse "+e.getClass().getName()+ " : "+e.getMessage(), logClassName);
				LoggerUtils.logException("", e, logClassName);
			}
		}
		LoggerUtils.log(LogType.MODIFY,Level.INFO,"Modify operation in primary cur is FAILED, so returning here..... " ,logClassName);
		return primaryCurResponse;
	}

	public void processModifyDualInNewCur(ModifyRequest modifyRequest, siemens.names.prov.gw.spml._2._0.ModifyRequest spmlRequest) {
		LoggerUtils.log(LogType.MODIFY, Level.INFO,"ModifyHandler:modify operation to start in secondary CUR ",logClassName);
		Date startTime = new Date();
		try {
			SoapClient soapClient = soapClientFactory.getClient(ASAPGWProperties.getPropertyValue(SOAP_PROTOCOL));
			LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Executing modify with spml request",logClassName);
			
			if(modLog.isDebugEnabled()) {
				String responseXml = xmlConverter.marshalRequestForLog(LogType.MODIFY,spmlRequest);
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, responseXml, logClassName);
			}
			
			siemens.names.prov.gw.spml._2._0.ModifyResponse spmlResponse = soapClient.executeModify(spmlRequest);
			
			if(modLog.isDebugEnabled()) {
				String responseXml = xmlConverter.marshalResponseForLog(LogType.MODIFY,spmlResponse);
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, responseXml, logClassName);
			}

			String identifier = null;
			if(modifyRequest.getIdentifier().getName().equalsIgnoreCase(UID))
				identifier = "[UID]"+modifyRequest.getIdentifier().getValue();
			if(modifyRequest.getIdentifier().getName().equalsIgnoreCase(USERNAME))
				identifier = "[USERNAME]"+modifyRequest.getIdentifier().getValue();
			if(modifyRequest.getIdentifier().getName().equalsIgnoreCase(VFSID))
				identifier = "[VFSID]"+modifyRequest.getIdentifier().getValue();

			AuditLogItemHelper.logDualProvAudit("MODIFY", identifier, startTime, spmlResponse);
			LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Finished modifying to secondary CUR",logClassName);
			
		} catch (SOAPDAOException ex) {
			LoggerUtils.log(LogType.MODIFY, Level.WARN, "ModifyHandler:SOAPDAOException " + ExceptionUtils.getRootCauseMessage(ex),logClassName);
		} catch (IOException e) {
			LoggerUtils.log(LogType.MODIFY,Level.WARN,"Exception while marshalling " , logClassName);
		}
	}

	
	/*
	 * Method to process multiple result objects with subobjects into respective parent objects
	 * 
	 */
	
	public static List<String> processObjList(List<String> rObj){

		List<String> outObj = new ArrayList<String>();

		if (rObj.contains(VFUSER)){
			outObj.add(VFUSER);
		}else {

			if (rObj.contains(VFSUBSCRIBER) && rObj.contains(VFSUBSERV)) {
				rObj.remove(VFSUBSERV);
			}
			else if (rObj.contains(VFUSERSERVICE) && rObj.contains(VFCREDENTIAL)) {
				rObj.remove(VFCREDENTIAL);
			}

			for (String eObj:rObj)
				outObj.add(eObj);
		}

		return outObj;

	}
	
	
	/**
	 * 
	 * @param modifyRequest
	 * @return
	 * @throws LDAPMappingException 
	 * @throws LDAPOperationFailureException 
	 * @throws LDAPDAOException 
	 */
	public ModifyResponse processModifyPrimary (ModifyRequest modifyRequest) throws LDAPMappingException, LDAPOperationFailureException, LDAPDAOException {
		ModifyResponse modifyResponse = new ModifyResponse();
		LdapResponse ldapResp = new LdapResponse();
		LoggerUtils.log(LogType.MODIFY,Level.INFO, "ModifyHandler: modifying in primary CUR first",logClassName);
		try {
			String run_mode = ASAPGWProperties.getPropertyValue(RUNMODE);
			User user = null;
			Subscriber subscr = null;
			IdentifierModifyType reqId = modifyRequest.getIdentifier();
			if (null == reqId) {
				LDAPOperationFailureException ex = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_IDENTIFIER, ex);
				ex.setErrorDetails("Identifier is empty");
				LoggerUtils.logException("ModifyHandler: Identifier is empty" , ex,logClassName);
				throw ex;
			} else if (StringUtils.isEmpty(reqId.getName()) || StringUtils.isEmpty(reqId.getValue())) {
				LDAPOperationFailureException excep = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_IDENTIFIER, excep);
				excep.setErrorDetails("Either Identifier Name or Value are missing in the request.");
				throw excep;
			} else if (reqId.getName().equalsIgnoreCase(UID)) {
				user = ldapClient.getUserMgr().get(reqId.getValue());
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Retrieved user by uid"+reqId.getValue()+"",logClassName);
			} else if (reqId.getName().equalsIgnoreCase(USERNAME)) {
				user = ldapClient.getUserMgr().getByUsername(reqId.getValue());
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Retrieved user by username"+reqId.getValue()+"",logClassName);
			} else if (reqId.getName().equalsIgnoreCase(VFSID)) {
				subscr = ldapClient.getSubscriberMgr().get(reqId.getValue());
				if (subscr != null)
					user = ldapClient.getUserMgr().get(subscr.getUid());
				else {
					LDAPOperationFailureException ex = new LDAPOperationFailureException();
					ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, ex);
					ex.setErrorDetails("Subscriber not found for user: " + reqId.getValue());
					LoggerUtils.logException("ModifyHandler: Subscriber not found" , ex,logClassName);
					throw ex;
				}
				LoggerUtils.log(LogType.MODIFY, "ModifyHandler: Retrieved user by MSISDN",logClassName);

			} else if (run_mode.equalsIgnoreCase("dual") && reqId.getName().equalsIgnoreCase(IMSI)) {
				LoggerUtils.log(LogType.MODIFY, Level.ERROR,"ModifyHandler: Modify attempted by IMSI in primary",logClassName);
				LDAPOperationFailureException excep = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_OP_NOT_SUPPORTED_DUAL_MODE, excep);
				LoggerUtils.logException("ModifyHandler: IMSI not supported" , excep,logClassName);
				throw excep;
			} else {
				LoggerUtils.log(LogType.MODIFY, Level.ERROR,"Invalid identifier in request",logClassName);
				LDAPOperationFailureException ex = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_IDENTIFIER, ex);
				ex.setErrorDetails("Invalid identifier specified "+ reqId.getValue());
				LoggerUtils.logException("ModifyHandler: Invalid identifier specified" , ex,logClassName);
				throw ex;
			}

			if(user == null) {
				LDAPOperationFailureException excep = new LDAPOperationFailureException();
				LoggerUtils.log(LogType.MODIFY,Level.ERROR, "ModifyHandler: User not found",logClassName);
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, excep);
				excep.setErrorDetails("There are no users for this identifier " + reqId.getValue());
				throw excep;
			}


			if ((null == modifyRequest.getVfuserDelete()) && (null == modifyRequest.getVfuserReplace())
					&& (null == modifyRequest.getVfuserAdd())) {
				LDAPOperationFailureException ex = new LDAPOperationFailureException();
				LoggerUtils.log(LogType.MODIFY,Level.ERROR, "ModifyHandler: Nothing to modify",logClassName);
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_NOTHING_TO_MODIFY, ex);
				ex.setErrorDetails("ModifyHandler: No user specified for update in modify request");
				LoggerUtils.logException("ModifyHandler: No user specified for update" , ex,logClassName);
				throw ex;
			}

			List<VfsubscriberType> inputSubscribers = new ArrayList<VfsubscriberType>();

			if(modifyRequest.getVfuserDelete() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserDelete().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserDelete().getVfsubscriber());
			}
			if(modifyRequest.getVfuserReplace() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserReplace().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserReplace().getVfsubscriber());
			}
			if(modifyRequest.getVfuserAdd() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserAdd().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserAdd().getVfsubscriber());
			}

			if(CollectionUtils.isNotEmpty(inputSubscribers)) {
				LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"total subscribers received in request " + inputSubscribers.size(),logClassName);
				Set subscribers = user.getSubscribers();
				if(CollectionUtils.isEmpty(subscribers)) {
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "There are no subscribers found in this user " +user.getId(),logClassName);
					LDAPOperationFailureException excep = new LDAPOperationFailureException();
					ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, excep);
					excep.setErrorDetails("There are no subscribers found in this user " +user.getId());
					throw excep;
				}

				else {
					for (VfsubscriberType input : inputSubscribers) {
						if (null == input.getVfsid() || StringUtils.isEmpty(input.getVfsid()) ){
							LoggerUtils.log(LogType.MODIFY, Level.ERROR,"ModifyHandler: vfsid not present in modify request",logClassName);
							LDAPOperationFailureException excep = new LDAPOperationFailureException();
							ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_MISSING_VFSID, excep);
							excep.setErrorDetails("ModifyHandler: vfsid not present in modify request");
							LoggerUtils.logException("ModifyHandler: vfsid not present in modify request", excep,logClassName);
							throw excep;
						}
						//if(StringUtils.isNotEmpty(input.getVfsid())) {
						else {
							boolean isExists = false;
							for (Object existing : subscribers) {
								Subscriber ldapsubscr = (Subscriber) existing;
								if(ldapsubscr.getMsisdn().equalsIgnoreCase(input.getVfsid())) {
									isExists = true;
									break;
								}
							}
							if(!isExists) {
								LDAPOperationFailureException excep = new LDAPOperationFailureException();
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Subscriber with vfsid[" +  input.getVfsid() +"] is not associated with the user ",logClassName);
								ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_UNRELATED_OBJECT_UPDATE_FAIL, excep);
								excep.setErrorDetails("Subscriber with vfsid[" +  input.getVfsid() +"] is not associated with the user ");
								throw excep;
							}
						}
					}
				}	
			}

			if(StringUtils.isNotEmpty(reqId.getOperationPrirotiy())) {
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Checking for the operation priority:",logClassName);
				for(char op : reqId.getOperationPrirotiy().toLowerCase().toCharArray()) {
					switch(op) {
					case 'a' :
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Modify-add is requested",logClassName);
						doLdapModifyAdd(modifyRequest, user); 
						break;
					case 'r' :
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Modify-replace is requested",logClassName);
						doLdapModifyReplace(modifyRequest, user); 
						break;
					case 'd' :
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Modify-delete is requested",logClassName);
						doLdapModifyDelete(modifyRequest, user); 
						break;
					default: break;
					}
				}
			} else {


				if (null != modifyRequest.getVfuserDelete())
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Do modify-delete",logClassName);	
				doLdapModifyDelete(modifyRequest, user); 
				if (null != modifyRequest.getVfuserReplace())
					LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"Do modify-replace",logClassName);
				doLdapModifyReplace(modifyRequest, user); 
				if (null != modifyRequest.getVfuserAdd())
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Do modify-add",logClassName);
				doLdapModifyAdd(modifyRequest, user); 
			}

			//Updating user before setting the response object
			user =  ldapClient.getUserMgr().get(user.getId());
			ldapResp.setUser(user);
			if(CollectionUtils.isNotEmpty(user.getSubscribers())) {
				for(Object sub : user.getSubscribers()) {
					if(sub != null) {
						ldapResp.getSubscribers().add((Subscriber) sub);
					}
				}
			}
			
		   if (modifyRequest.getReturn() != null) {
				ldapResp.setReturnScope(modifyRequest.getReturn().getResultingObject());
				if(CollectionUtils.isNotEmpty(modifyRequest.getReturn().getResultingObjectClass()))
					ldapResp.setReturnScopeObjectClass(modifyRequest.getReturn().getResultingObjectClass());
			}

			modifyResponse = new ModifyResponse();
			try {

				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Mapping the response to Asa-PGW",logClassName);
				LDAPResponseMapper.ldapToAsaPgw(LogType.MODIFY,ldapResp, modifyResponse);
			} catch (Exception ex) {
				LoggerUtils.log(LogType.MODIFY,Level.ERROR,"LDAPMappingException while adding user :" , logClassName);
				LoggerUtils.logException("LDAPMappingException while adding user :" , ex,logClassName);
				LDAPMappingException excep = new LDAPMappingException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_RESP_MAPG_ERR, excep);
				excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
				throw excep;
			}

		} catch (LDAPMappingException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"LDAPMappingException while adding user" ,logClassName);
			LoggerUtils.logException("LDAPMappingException while adding user" , ex,logClassName);
			LDAPMappingException excep = new LDAPMappingException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_REQ_MAPG_ERR, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurInvalidOperationException|CurApiInternalError|CurOperationNotSupportedException|CurMethodFailureException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"Any of CurInvalidOperationException,CurApiInternalError,CurOperationNotSupportedException,CurMethodFailureException while modifying user " ,logClassName);
			LoggerUtils.logException("Any of CurInvalidOperationException,CurApiInternalError,CurOperationNotSupportedException,CurMethodFailureException while modifying user " , ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_OP_FAILURE, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurOperationalLimitException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"CurOperationalLimitException while modifying user : " ,logClassName);
			LoggerUtils.logException("CurOperationalLimitException while modifying user  " , ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_OP_FAILURE, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurSchemaViolationException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"CurSchemaViolationException while modifying user :" ,logClassName);
			LoggerUtils.logException("CurSchemaViolationException while modifying user :" , ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_SCHEMA, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurCommunicationException|CurConfigurationException|CurSecurityException|CurUnavailableException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.FATAL,"Any of CurCommunicationException,CurConfigurationException,CurSecurityException,CurUnavailableException  in  :" ,logClassName);
			LoggerUtils.logException("Any of CurCommunicationException,CurConfigurationException,CurSecurityException,CurUnavailableException  in :" , ex,logClassName);
			LDAPDAOException excep = new LDAPDAOException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurSystemException|CurException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.FATAL,"CurException while modifying user :",logClassName);
			LoggerUtils.logException("CurException while modifying user :", ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		}
		LoggerUtils.log(LogType.MODIFY, Level.INFO,"ModifyHandler: finished modify in Primary CUR",logClassName);
		return modifyResponse;
	}

	/**
	 * @param modifyRequest
	 * @param user
	 * @param subscr
	 * @throws LDAPMappingException
	 * @throws CurInvalidOperationException
	 * @throws LDAPOperationFailureException 
	 */
	private void doLdapModifyAdd(ModifyRequest modifyRequest, User user)
			throws LDAPMappingException, CurInvalidOperationException, LDAPOperationFailureException {
		try {
			// MODIFY REQUEST --> ADD
			if(modifyRequest.getVfuserAdd() != null) {
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:performing modify-add in primary CUR first",logClassName);

				CurManager curManager = ldapClient.getCurMgr();
				UserManager userMgr = ldapClient.getUserMgr();
				SubscriberManager subscrMgr = ldapClient.getSubscriberMgr();
				CredentialManager credMgr = ldapClient.getCredentialMgr();

				VfuserType vfuser = modifyRequest.getVfuserAdd();

				//UID must not get updated my modify
				vfuser.setUid(null);

				//Print ASAPGW User object
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Modify request: vfuser object before mapping to LDAP",logClassName);
				//LoggerUtils.logFullVfuser(LogType.MODIFY, ModifyHandler.class.toString(), vfuser);
				//map to User details
				CurObjectMapper.mapToLdapUser(vfuser, user, "add");
				LoggerUtils.log(LogType.ADD, "LDAP User after mapping",logClassName);
				LoggerUtils.logLdapVfuser(LogType.ADD, ModifyHandler.class.toString(),user);
				//map AAService details
				if(vfuser.getVfukuseraaservice() != null) {
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Modifying user-aaservice",logClassName);
					//Obtain AAUserProfile from CurManager
					AAUserProfile aaUserProfile = null;
					Credential ldapCredentl = null;
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start : user-aaservice",logClassName);
					aaUserProfile = (AAUserProfile) user.getProfile(AAUser.AA_TYPE);
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end : user-aaservice",logClassName);

					if (aaUserProfile == null){
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start : Add user-aaservice",logClassName);
						aaUserProfile = (AAUserProfile) curManager.addProfile(user, AAUser.AA_TYPE);
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end : Add user-aaservice",logClassName);
					}

					CurObjectMapper.mapToLdapAAProfile(vfuser.getVfukuseraaservice(), aaUserProfile, "add");
					LoggerUtils.log(LogType.ADD, "LDAP aaUser after mapping",logClassName);
					LoggerUtils.logLdapAAUser(LogType.ADD, ModifyHandler.class.toString(),aaUserProfile);

					if(CollectionUtils.isNotEmpty(vfuser.getVfukuseraaservice().getVfukaacredential())) {
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Modifying credential",logClassName);
						for (VfukaacredentialType credential : vfuser.getVfukuseraaservice().getVfukaacredential()) {
							if (credential != null) {
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start : get credential",logClassName);
								ldapCredentl = aaUserProfile.getCredential(credential.getVfukcredential());
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end: get credential",logClassName);
								boolean isNewCred = false;
								if (null == ldapCredentl) {
									isNewCred = true;
									if("password".equalsIgnoreCase(credential.getVfukcredential())) {
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start :Modifying password credential",logClassName);
										ldapCredentl = credMgr.create(Credential.PASSWORD_TYPE);
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end :Modifying password credential",logClassName);
									} else if ("pin".equalsIgnoreCase(credential.getVfukcredential())) {
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start :Modifying pin credential",logClassName);
										ldapCredentl =  credMgr.create(Credential.PIN_TYPE);
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start :Modifying pin credential",logClassName);
									} else if ("secret-answer".equalsIgnoreCase(credential.getVfukcredential())) {
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start :Modifying secret-answer credential",logClassName);
										ldapCredentl =  credMgr.create(Credential.SECRET_ANSWER_TYPE);
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end :Modifying secret-answer credential",logClassName);
									} else if ("otac".equalsIgnoreCase(credential.getVfukcredential())) {
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start :Modifying otac credential",logClassName);
										ldapCredentl =  credMgr.create(Credential.OTAC_TYPE);
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end :Modifying otac credential",logClassName);
									} else if ("ecrm-pin".equalsIgnoreCase(credential.getVfukcredential())) {
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start :Modifying ecrm-pin credential",logClassName);
										ldapCredentl =  credMgr.create(credential.getVfukcredential());
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end :Modifying ecrm-pin credential",logClassName);
									} 
								}
								if (ldapCredentl != null) {
									LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Adding credential",logClassName);
									CurObjectMapper.mapToLdapCredential(credential, ldapCredentl, isNewCred);
									LoggerUtils.log(LogType.ADD, "LDAP aaCredential after mapping",logClassName);
									LoggerUtils.logLdapAACredential(LogType.ADD, ModifyHandler.class.toString(),ldapCredentl);
									aaUserProfile.addCredential(ldapCredentl);
								}
							}
						}
					}
				}

				// commit user changes
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start:Committing User",logClassName);
				userMgr.commit(user);
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end:Committing User",logClassName);

				//addCSRS
				if(vfuser.getVfukuseraaservice() != null) {
					AAUserProfile aaUserProfile = (AAUserProfile) user.getProfile(AAUser.AA_TYPE);
					List<String> csrs = vfuser.getVfukuseraaservice().getVfukcsrs();
					if(CollectionUtils.isNotEmpty(csrs) && aaUserProfile != null) {
						CurObjectMapper.handleCSRs(csrs, aaUserProfile, user.getId(), "add");
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start:Committing User again",logClassName);
						userMgr.commit(user);
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start:Committing User again",logClassName);
					}
				}

				//add subscriber data
				if(CollectionUtils.isNotEmpty(vfuser.getVfsubscriber())) {
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Modifying subscriber",logClassName);
					for (VfsubscriberType vfsubscr : vfuser.getVfsubscriber()) {
						if(vfsubscr != null) {

							//vfsid is mandatory to pass in request
							if (null == vfsubscr.getVfsid()){
								LDAPOperationFailureException excep = new LDAPOperationFailureException();
								ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_MISSING_VFSID, excep);
								excep.setErrorDetails("ModifyHandler: vfsid not present in modify request");
								LoggerUtils.logException("ModifyHandler: vfsid not present in modify request", excep,logClassName);
								throw excep;
							} 

							LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start : subscriber get " + vfsubscr.getVfsid(),logClassName);
							Subscriber ldapSubscr = subscrMgr.get(vfsubscr.getVfsid());
							LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end : subscriber get" + vfsubscr.getVfsid(),logClassName);
							CurObjectMapper.mapToLdapSubscriber(vfsubscr, ldapSubscr, "add");
							LoggerUtils.log(LogType.ADD, "LDAP Subscriber after mapping",logClassName);
							LoggerUtils.logLdapVfsubscriber(LogType.ADD, ModifyHandler.class.toString(),ldapSubscr);
							//get AASubscriber profile
							if(vfsubscr.getVfuksubaaservice() != null) {
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Modifying subscriber aaservice",logClassName);
								AASubscriberProfile aaSubscrProfile = null;
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start : subscriber aaget",logClassName);
								aaSubscrProfile = (AASubscriberProfile) ldapSubscr.getProfile(AASubscriber.AA_TYPE);
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end : subscriber aaget",logClassName);
								if(null == aaSubscrProfile){
									LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start : AAService is not present..adding...",logClassName);
									aaSubscrProfile = (AASubscriberProfile) curManager.addProfile(ldapSubscr, AASubscriber.AA_TYPE);
									LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end : AAService is not present..adding...",logClassName);
								}
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Mapping Subscriber AAService",logClassName);
								CurObjectMapper.mapToLdapAASubscriber(vfsubscr.getVfuksubaaservice(), aaSubscrProfile, "add");
								LoggerUtils.log(LogType.ADD, "LDAP Subscriber after mapping",logClassName);
								LoggerUtils.logLdapAAVfsubscriber(LogType.ADD, ModifyHandler.class.toString(),aaSubscrProfile);
							}
							LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start : subscriber commit",logClassName);
							subscrMgr.commit(ldapSubscr);
							LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end : subscriber commit",logClassName);
						}
					}
				}
			}
		} catch (CurException exc ) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"ModifyHandler: LDAP Backend error",logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(exc.getMessage());
			LoggerUtils.logException("ModifyHandler: LDAP Backend error", excep,logClassName);
			throw excep;
		} catch (CurSystemException cse) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"ModifyHandler: LDAP Backend error",logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(cse.getMessage());
			LoggerUtils.logException("ModifyHandler: LDAP Backend error", excep,logClassName);
			throw excep;
		}
	}

	/**
	 * @param modifyRequest
	 * @param user
	 * @throws LDAPMappingException
	 * @throws CurInvalidOperationException
	 * @throws LDAPOperationFailureException 
	 */
	private void doLdapModifyReplace(ModifyRequest modifyRequest, User user) throws LDAPMappingException, CurInvalidOperationException, LDAPOperationFailureException {
		// MODIFY REQUEST --> REPLACE
		try {
			if(modifyRequest.getVfuserReplace() != null) {
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: performing modify-replace in primary CUR first",logClassName);

				CurManager curManager = ldapClient.getCurMgr();
				UserManager userMgr = ldapClient.getUserMgr();
				SubscriberManager subscrMgr = ldapClient.getSubscriberMgr();
				CredentialManager credMgr = ldapClient.getCredentialMgr();

				VfuserType vfuser = modifyRequest.getVfuserReplace();
				//Print ASAPGW User object
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Modify request: vfuser object before mapping to LDAP",logClassName);
				//LoggerUtils.logFullVfuser(LogType.MODIFY, ModifyHandler.class.toString(), vfuser);
				//map to User details
				CurObjectMapper.mapToLdapUser(vfuser, user, "replace");
				LoggerUtils.log(LogType.ADD, "LDAP User after mapping",logClassName);
				LoggerUtils.logLdapVfuser(LogType.ADD, ModifyHandler.class.toString(),user);
				LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"ModifyHandler: Mapped to CUR USER",logClassName);
				//map AAService details
				if(vfuser.getVfukuseraaservice() != null) {
					//Obtain AAUserProfile from CurManager
					AAUserProfile aaUsrProfile = null;
					Credential ldapCredentl = null;
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call start : user-aaservice",logClassName);
					aaUsrProfile = (AAUserProfile) user.getProfile(AAUser.AA_TYPE);
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ldap call end : user-aaservice",logClassName);
					if (aaUsrProfile != null) {
						LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"ModifyHandler: Retrieved user aaservice from cur",logClassName);
						CurObjectMapper.mapToLdapAAProfile(vfuser.getVfukuseraaservice(), aaUsrProfile, "replace");
						LoggerUtils.log(LogType.ADD, "LDAP aaUser after mapping",logClassName);
						LoggerUtils.logLdapAAUser(LogType.ADD, ModifyHandler.class.toString(),aaUsrProfile);
					} else {
						aaUsrProfile = (AAUserProfile) curManager.addProfile(user, AAUser.AA_TYPE);
						LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Added new user aaservice from cur",logClassName);
						CurObjectMapper.mapToLdapAAProfile(vfuser.getVfukuseraaservice(), aaUsrProfile, "replace");
						LoggerUtils.log(LogType.ADD, "LDAP aaUser after mapping",logClassName);
						LoggerUtils.logLdapAAUser(LogType.ADD, ModifyHandler.class.toString(),aaUsrProfile);
					}

					if(CollectionUtils.isNotEmpty(vfuser.getVfukuseraaservice().getVfukaacredential())) {

						//if already contains credentials
						if (CollectionUtils.isNotEmpty(aaUsrProfile.getCredentials())) {
							for (Object crdobj : aaUsrProfile.getCredentials()) {
								if (crdobj != null) {
									Credential ldapcred = (Credential) crdobj;
								}
							}
						}

						for (VfukaacredentialType credential : vfuser.getVfukuseraaservice().getVfukaacredential()) {
							if (credential != null) {
								ldapCredentl = aaUsrProfile.getCredential(credential.getVfukcredential());
								boolean isNewCred = false;
								if (null == ldapCredentl) {
									isNewCred = true;
									if("password".equalsIgnoreCase(credential.getVfukcredential())) {
										ldapCredentl = credMgr.create(Credential.PASSWORD_TYPE);
									} else if ("pin".equalsIgnoreCase(credential.getVfukcredential())) {
										ldapCredentl =  credMgr.create(Credential.PIN_TYPE);
									} else if ("secret-answer".equalsIgnoreCase(credential.getVfukcredential())) {
										ldapCredentl =  credMgr.create(Credential.SECRET_ANSWER_TYPE);
									} else if ("otac".equalsIgnoreCase(credential.getVfukcredential())) {
										ldapCredentl =  credMgr.create(Credential.OTAC_TYPE);
									} else if ("ecrm-pin".equalsIgnoreCase(credential.getVfukcredential())) {
										ldapCredentl =  credMgr.create(credential.getVfukcredential());
									} 
								}
								if (ldapCredentl != null) {
									CurObjectMapper.mapToLdapCredential(credential, ldapCredentl, isNewCred);
									LoggerUtils.log(LogType.ADD, "LDAP aaCredential after mapping",logClassName);
									LoggerUtils.logLdapAACredential(LogType.ADD, ModifyHandler.class.toString(),ldapCredentl);
									aaUsrProfile.addCredential(ldapCredentl);
								}

							}
						}
					}
				}
				// commit user changes
				userMgr.commit(user);

				//addCSRS
				if(vfuser.getVfukuseraaservice() != null) {
					AAUserProfile aaUserProfile = (AAUserProfile) user.getProfile(AAUser.AA_TYPE);
					List<String> csrs = vfuser.getVfukuseraaservice().getVfukcsrs();
					if(CollectionUtils.isNotEmpty(csrs) && aaUserProfile != null) {
						CurObjectMapper.handleCSRs(csrs, aaUserProfile, user.getId(), "replace");
						userMgr.commit(user);
					}
				}

				//add subscriber data
				if(CollectionUtils.isNotEmpty(vfuser.getVfsubscriber())) {
					LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"ModifyHandler:Modifying subscriber",logClassName);
					for (VfsubscriberType vfsubscr : vfuser.getVfsubscriber()) {
						if(vfsubscr != null) {

							//vfsid is mandatory to pass in request
							if (null == vfsubscr.getVfsid()){
								LoggerUtils.log(LogType.MODIFY, Level.ERROR,"ModifyHandler: vfsid not present in modify request",logClassName);
								LDAPOperationFailureException excep = new LDAPOperationFailureException();
								ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_MISSING_VFSID, excep);
								excep.setErrorDetails("ModifyHandler: vfsid not present in modify request");
								LoggerUtils.logException("ModifyHandler: vfsid not present in modify request", excep,logClassName);
								throw excep;
							} 

							LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Modifying subscriber - " + vfsubscr.getVfsid(),logClassName);
							Subscriber ldapSubscr = subscrMgr.get(vfsubscr.getVfsid());

							CurObjectMapper.mapToLdapSubscriber(vfsubscr, ldapSubscr, "replace");
							LoggerUtils.log(LogType.ADD, "LDAP Subscriber after mapping",logClassName);
							LoggerUtils.logLdapVfsubscriber(LogType.ADD, ModifyHandler.class.toString(),ldapSubscr);
							//get AASubscriber profile
							if(vfsubscr.getVfuksubaaservice() != null) {
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Modifying subscriber aaservice",logClassName);
								AASubscriberProfile aaSubscrProfile = null;

								aaSubscrProfile = (AASubscriberProfile) ldapSubscr.getProfile(AASubscriber.AA_TYPE);
								if(null == aaSubscrProfile){
									LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Subscriber AAService is not present..adding...",logClassName);
									aaSubscrProfile = (AASubscriberProfile) curManager.addProfile(ldapSubscr, AASubscriber.AA_TYPE);
								}
								CurObjectMapper.mapToLdapAASubscriber(vfsubscr.getVfuksubaaservice(), aaSubscrProfile, "replace");
								LoggerUtils.log(LogType.ADD, "LDAP Subscriber after mapping",logClassName);
								LoggerUtils.logLdapAAVfsubscriber(LogType.ADD, ModifyHandler.class.toString(),aaSubscrProfile);
							}
							LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler:Committing Subscriber",logClassName);
							subscrMgr.commit(ldapSubscr);
						}
					}
				}
			}
		} catch (CurException exc ) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"ModifyHandler: LDAP Backend error",logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(exc.getMessage());
			LoggerUtils.logException("ModifyHandler: LDAP Backend error", excep,logClassName);
			throw excep;
		} catch (CurSystemException cse) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"ModifyHandler: LDAP Backend error",logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(cse.getMessage());
			LoggerUtils.logException("ModifyHandler: LDAP Backend error", excep,logClassName);
			throw excep;
		}
	}

	/**
	 * @param modifyRequest
	 * @param user
	 * @param userMgr
	 * @param subscrMgr
	 * @throws LDAPMappingException
	 * @throws CurException 
	 * @throws LDAPOperationFailureException 
	 */
	private void doLdapModifyDelete(ModifyRequest modifyRequest, User user) throws LDAPMappingException, CurException, LDAPOperationFailureException {
		// MODIFY REQUEST --> DELETE
		LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"Inside Modify Delete",logClassName);
		try {
			if(modifyRequest.getVfuserDelete() != null) {
				LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"ModifyHandler: performing modify-delete in primary CUR first",logClassName);

				UserManager userMgr = ldapClient.getUserMgr();
				SubscriberManager subscrMgr = ldapClient.getSubscriberMgr();

				VfuserType vfuser = modifyRequest.getVfuserDelete();

				//add subscriber data
				if(CollectionUtils.isNotEmpty(vfuser.getVfsubscriber())) {
					Subscriber ldapSubscr = null;
					for (VfsubscriberType vfsubscr : vfuser.getVfsubscriber()) {
						if(vfsubscr != null) {
							//vfsid is mandatory to pass in request
							if (null == vfsubscr.getVfsid()){
								LoggerUtils.log(LogType.MODIFY,Level.ERROR,"ModifyHandler: vfsid not present in modify request",logClassName);
								LDAPOperationFailureException excep = new LDAPOperationFailureException();
								ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_MISSING_VFSID, excep);
								excep.setErrorDetails("ModifyHandler: vfsid not present in modify request");
								LoggerUtils.logException("ModifyHandler: vfsid not present in modify request", excep,logClassName);
								throw excep;
							} 

							ldapSubscr = subscrMgr.get(vfsubscr.getVfsid());

							if(vfsubscr.getVfuksubaaservice() != null) {
								String deleteFlag = vfsubscr.getVfuksubaaservice().getDeleteObject();
								// delete the entire profile
								AASubscriberProfile aaSubscrProfile = (AASubscriberProfile) ldapSubscr.getProfile(AASubscriber.AA_TYPE);
								if(StringUtils.isNotEmpty(deleteFlag) && Boolean.valueOf(deleteFlag)) {
									LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: deleting the entire AAProfile form the subscriber " + vfsubscr.getVfsid() ,logClassName);
									if (aaSubscrProfile != null) {
										ldapSubscr.removeProfile(aaSubscrProfile);
									}
								} else {
									// individual attribute deletion
									if (aaSubscrProfile != null) {
										CurObjectMapper.ldapAASubscriberProfileRemoveAttributeMapper(vfsubscr.getVfuksubaaservice(), aaSubscrProfile);
									}
								}
							}

							CurObjectMapper.ldapSubscriberRemoveAttributeMapper(vfsubscr, ldapSubscr);

							subscrMgr.commit(ldapSubscr);
						}
					}
				}

				// full sub-objects (like credentials, aaservice) deletion
				if(vfuser.getVfukuseraaservice() != null) {
					String deleteFlag = vfuser.getVfukuseraaservice().getDeleteObject();
					AAUserProfile aaUsrProfile = (AAUserProfile) user.getProfile(AAUser.AA_TYPE);
					if (aaUsrProfile != null) {
						// delete the entire profile
						if(StringUtils.isNotEmpty(deleteFlag) && Boolean.valueOf(deleteFlag)) {
							LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"ModifyHandler: deleting the entire AAProfile form the user " + user.getId() ,logClassName);
							user.removeProfile(aaUsrProfile);
						} else {

							//first check credential objects
							if(CollectionUtils.isNotEmpty(vfuser.getVfukuseraaservice().getVfukaacredential())) {
								Credential credential = null;
								for (VfukaacredentialType  inputCred : vfuser.getVfukuseraaservice().getVfukaacredential()) {
									if(inputCred != null && inputCred.getDeleteObject() != null && Boolean.valueOf(inputCred.getDeleteObject())) {
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: deleting the AACredential type " + inputCred.getVfukcredential(), logClassName);
										credential = aaUsrProfile.getCredential(inputCred.getVfukcredential());
										if(credential != null)
											aaUsrProfile.removeCredential(credential);
									} else {
										LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Deleting attributes from credential object", logClassName);
										credential = aaUsrProfile.getCredential(inputCred.getVfukcredential());
										// individual attribute deletion
										if(credential != null)
											CurObjectMapper.ldapCredentialRemoveAttributeMapper(inputCred, credential);
									}
								}
							}
							// individual attribute deletion
							CurObjectMapper.ldapAAUserProfileRemoveAttributeMapper(vfuser.getVfukuseraaservice(), aaUsrProfile);
						}
					}
				}
				// individual attribute deletion
				CurObjectMapper.ldapUserRemoveAttributeMapper(vfuser, user);
				// commit user changes
				userMgr.commit(user);
			}
		} catch (CurException exc ) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"ModifyHandler: LDAP Backend error",logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(exc.getMessage());
			LoggerUtils.logException("ModifyHandler: LDAP Backend error", excep,logClassName);
			throw excep;
		} catch (CurSystemException cse) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"ModifyHandler: LDAP Backend error",logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(cse.getMessage());
			LoggerUtils.logException("ModifyHandler: LDAP Backend error", excep,logClassName);
			throw excep;
		}
	}

	/**
	 * 
	 * @param modifyRequest
	 * @return
	 * @throws LDAPDAOException 
	 */
	public ModifyResponse processModifySecondary (ModifyRequest modifyRequest, AuditLogItem auditLogItem) throws LDAPOperationFailureException, SPMLOperationFailureException, SPMLMappingException, SOAPDAOException, LDAPDAOException {
		//web service invocation -- no threads
		LoggerUtils.log(LogType.MODIFY, Level.INFO, "ModifyHandler:modify operation to start in secondary CUR ",logClassName);
		ModifyResponse modifyResponse = null;

		siemens.names.prov.gw.spml._2._0.ModifyRequest spmlRequest = new siemens.names.prov.gw.spml._2._0.ModifyRequest();
		siemens.names.prov.gw.spml._2._0.ModifyResponse spmlResponse = null;
//		User user = null;
//		Subscriber subscr = null;
		User_AsaPgw user = null;
		Subscriber_Asapgw subscr = null;
		CurManager curMgr = ldapClient.getCurMgr();
		LoggerUtils.log(LogType.MODIFY, Level.INFO,"modify operation - business validations are started",logClassName);
		try {
			IdentifierModifyType reqId = modifyRequest.getIdentifier();

			if(StringUtils.isEmpty(reqId.getName()) || StringUtils.isEmpty(reqId.getValue())) {
				LDAPOperationFailureException excep = new LDAPOperationFailureException();
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_IDENTIFIER, excep);
				excep.setErrorDetails("Either Identifier Name or Value are missing in the request.");
				throw excep;
			}

			if (reqId.getName().equalsIgnoreCase(UID)) {
//				user = ldapClient.getUserMgr().get(reqId.getValue());
				user = curMgr.getAsaPgwUser(UID, reqId.getValue());
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Retrieved user by uid "+reqId.getValue(),logClassName);
			} else if (reqId.getName().equalsIgnoreCase(USERNAME)) {
//				user = ldapClient.getUserMgr().getByUsername(reqId.getValue());
				user = curMgr.getAsaPgwUser(USERNAME, reqId.getValue());
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Retrieved user by username "+reqId.getValue(),logClassName);
			} else if (reqId.getName().equalsIgnoreCase(VFSID)) {
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: Retrieved subscriber by vfsid "+reqId.getValue(),logClassName);
//				subscr = ldapClient.getSubscriberMgr().get(reqId.getValue());
				user = curMgr.getAsaPgwUser(VFSID, reqId.getValue());
//				if(subscr == null) {
				if(user == null) {
					LDAPOperationFailureException excep = new LDAPOperationFailureException();
					LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"ModifyHandler: Subscriber is not found ",logClassName);
					ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, excep);
					excep.setErrorDetails("Subscriber is not found for identifier "+reqId.getValue());
					throw excep;
				}
//				user = ldapClient.getUserMgr().get(subscr.getUid());
//				LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"ModifyHandler: Retrieved user by MSISDN "+subscr.getUid()+"",logClassName);
			}

			if(user == null) {
				LDAPOperationFailureException excep = new LDAPOperationFailureException();
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler: User not found",logClassName);
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, excep);
				excep.setErrorDetails("User not found for "+ reqId.getValue());
				throw excep;
			}

			if ((null == modifyRequest.getVfuserDelete()) && (null == modifyRequest.getVfuserReplace())
					&& (null == modifyRequest.getVfuserAdd())) {
				SPMLOperationFailureException ex = new SPMLOperationFailureException();
				LoggerUtils.log(LogType.MODIFY,Level.ERROR, "ModifyHandler: Nothing to modify",logClassName);
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_NOTHING_TO_MODIFY, ex);
				ex.setErrorDetails("ModifyHandler: No user specified for update in modify request");
				LoggerUtils.logException("ModifyHandler: No user specified for update" , ex,logClassName);
				throw ex;
			}

			List<VfsubscriberType> inputSubscribers = new ArrayList<VfsubscriberType>();

			if(modifyRequest.getVfuserDelete() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserDelete().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserDelete().getVfsubscriber());
			}
			if(modifyRequest.getVfuserReplace() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserReplace().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserReplace().getVfsubscriber());
			}
			if(modifyRequest.getVfuserAdd() != null && CollectionUtils.isNotEmpty(modifyRequest.getVfuserAdd().getVfsubscriber())) {
				inputSubscribers.addAll(modifyRequest.getVfuserAdd().getVfsubscriber());
			}

			if(CollectionUtils.isNotEmpty(inputSubscribers)) {
				LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"total subscribers received in request " + inputSubscribers.size(),logClassName);
//				Set subscribers = user.getSubscribers();
				List<Subscriber_Asapgw> subscribers = user.getSubscribers();
				if(CollectionUtils.isEmpty(subscribers)) {
					LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "There are no subscribers found in logClassName user " +user.getUid(),logClassName);
					LDAPOperationFailureException excep = new LDAPOperationFailureException();
					ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_PROFILE_NOT_FOUND, excep);
					excep.setErrorDetails("There are no subscribers found in logClassName user " +user.getUid());
					throw excep;
				} else {
					for (VfsubscriberType input : inputSubscribers) {
						if (null == input.getVfsid() || StringUtils.isEmpty(input.getVfsid()) ){
							LoggerUtils.log(LogType.MODIFY, Level.ERROR,"ModifyHandler: vfsid not present in modify request",logClassName);
							LDAPOperationFailureException excep = new LDAPOperationFailureException();
							ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_MISSING_VFSID, excep);
							excep.setErrorDetails("ModifyHandler: vfsid not present in modify request");
							LoggerUtils.logException("ModifyHandler: vfsid not present in modify request", excep,logClassName);
							throw excep;
						}
						//if(StringUtils.isNotEmpty(input.getVfsid())) {
						else {	
							boolean isExists = false;
							for (Subscriber_Asapgw existing : subscribers) {
//								Subscriber ldapsubscr = (Subscriber) existing;
								if(existing.getMsisdn().equalsIgnoreCase(input.getVfsid())) {
									isExists = true;
									break;
								}
							}
							if(!isExists) {
								LDAPOperationFailureException excep = new LDAPOperationFailureException();
								LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Subscriber with vfsid[" +  input.getVfsid() +"] is not associated with the user ",logClassName);
								ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_UNRELATED_OBJECT_UPDATE_FAIL, excep);
								excep.setErrorDetails("Subscriber with vfsid[" +  input.getVfsid() +"] is not associated with the user ");
								throw excep;
							}
						}

					}
				}
			}

			LoggerUtils.log(LogType.MODIFY, Level.INFO,"modify operation - business validations are completed",logClassName);

		} catch (CurMethodFailureException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"CurMethodFailureException while modifying user : " ,logClassName);
			LoggerUtils.logException("CurMethodFailureException while modifying user : " , ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_LDAP_OP_FAILURE, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurSchemaViolationException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"CurSchemaViolationException while modifying user :" ,logClassName);
			LoggerUtils.logException("CurSchemaViolationException while modifying user :" , ex,logClassName);
			LDAPOperationFailureException excep = new LDAPOperationFailureException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_INVALID_SCHEMA, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurCommunicationException|CurConfigurationException|CurSecurityException|CurUnavailableException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"Any of CurCommunicationException,CurConfigurationException,CurSecurityException,CurUnavailableException  in  :" ,logClassName);
			LoggerUtils.logException("Any of CurCommunicationException,CurConfigurationException,CurSecurityException,CurUnavailableException  in :" , ex,logClassName);
			LDAPDAOException excep = new LDAPDAOException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		} catch (CurSystemException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.ERROR,"CurSystemException while modifying user :" ,logClassName);
			LoggerUtils.logException("CurSystemException while modifying user :" , ex,logClassName);
			LDAPDAOException excep = new LDAPDAOException();
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.INTERNAL_SYSTEM_ERR, excep);
			excep.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw excep;
		}

		try {
			LoggerUtils.log(LogType.MODIFY,Level.INFO, "Mapping to SPMLmodify request is started",logClassName);
			SPMLRequestMapper.aspgwToSpml(LogType.MODIFY,modifyRequest, spmlRequest, user);
			LoggerUtils.log(LogType.MODIFY,Level.INFO, "Mapping to SPMLmodify is completed",logClassName);
		} catch (SPMLMappingException ex) {
			LoggerUtils.log(LogType.MODIFY, Level.DEBUG,"ModifyHandler : spml mapping error",logClassName);
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_SPML_REQ_MAPG_ERR, ex);
			ex.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw ex;
		}

		SoapClient soapClient = soapClientFactory.getClient(ASAPGWProperties.getPropertyValue(SOAP_PROTOCOL));
		LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "Executing modify with spml request",logClassName);
		if(modLog.isDebugEnabled()) {
			try {
				String responseXml = xmlConverter.marshalRequestForLog(LogType.MODIFY,spmlRequest);
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, responseXml, logClassName);
			} catch (IOException e) {
				LoggerUtils.log(LogType.MODIFY,Level.ERROR,"Exception while marshalling " ,logClassName);
				SPMLOperationFailureException ex = new SPMLOperationFailureException();
				LoggerUtils.logException("Exception while marshalling " , e,logClassName);
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,Enum.valueOf(ASAPGWStatusCodes.class, LogType.MODIFY.name()+"_XML_MARSHAL_ERR"), ex);
				ex.setErrorDetails(ExceptionUtils.getRootCauseMessage(e));
				throw ex;
			}
		}
		spmlResponse = soapClient.executeModify(spmlRequest);
		if(modLog.isDebugEnabled()) {
			try {
				String responseXml = xmlConverter.marshalResponseForLog(LogType.MODIFY,spmlResponse);
				LoggerUtils.log(LogType.MODIFY,Level.DEBUG, responseXml, logClassName);
			} catch (IOException e) {
				LoggerUtils.log(LogType.MODIFY,Level.ERROR,"Exception while marshalling " ,logClassName);
				SPMLOperationFailureException ex = new SPMLOperationFailureException();
				LoggerUtils.logException("Exception while marshalling " , e,logClassName);
				ASAPGWUtils.populateErrorCodes(LogType.MODIFY,Enum.valueOf(ASAPGWStatusCodes.class, LogType.MODIFY.name()+"_XML_MARSHAL_ERR"), ex);
				ex.setErrorDetails(ExceptionUtils.getRootCauseMessage(e));
				throw ex;
			}
		}
		modifyResponse = new ModifyResponse();
		try {
			LoggerUtils.log(LogType.MODIFY, Level.INFO,"Mapping response to Asa-Pgw",logClassName);
			SPMLResponseMapper.spmlToAsapgw(LogType.MODIFY,spmlResponse,modifyRequest.getReturn(), modifyResponse);
			LoggerUtils.log(LogType.MODIFY, Level.INFO,"Modify response after mapping to asapgw",logClassName);
			//			LoggerUtils.logFullVfuser(LogType.MODIFY, ModifyHandler.class.toString(),modifyResponse.getResultObject().getVfuser());
		} catch (SPMLMappingException ex) {
			LoggerUtils.log(LogType.MODIFY,Level.DEBUG, "ModifyHandler : spml mapping error",logClassName);
			ASAPGWUtils.populateErrorCodes(LogType.MODIFY,ASAPGWStatusCodes.MODIFY_SPML_RESP_MAPG_ERR, ex);
			ex.setErrorDetails(ExceptionUtils.getRootCauseMessage(ex));
			throw ex;
		}

		LoggerUtils.log(LogType.MODIFY, Level.INFO, "ModifyHandler:Finished modifying to secondary CUR",logClassName);
		
		if (ASAPGWConstants.SUCCESS_CODE.equals(modifyResponse.getResultStatus().getResultCode())) {
			asapMessageHandler.processRequestForASAP(modifyRequest, auditLogItem.getStartTime());
		}
		
		return modifyResponse;
	}

	/*
	 * 
	 * Below methods only for Subadmin services.
	 * 
	 */
	public boolean processModifyDual(SubscriberState request, SubscriberManager subMgr, Subscriber subscriber) throws AttributeTableException, SPMLMappingException, SOAPDAOException, SPMLOperationFailureException {
		
		boolean modified = false;
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "InsideModifyHandler :: processModifyDual " + request.getMSISDNN(), logClassName);
		
		/** Release 1.2.10 changes - SubAdmin performance improvements
		  * 
		SubscriberManager subMgr = ldapClient.getSubscriberMgr();
		Subscriber subscriber = subMgr.get(request.getMSISDNN());   **/
		
		/* Perform Operation in Primary CUR first */
		modified = processModifyPrimary(request, subMgr, null);

		if (modified) {
			
			asyncService.processModifySecondary(request,subAdminUtils,subscriber);
			
			try {
				Thread.sleep(Integer.parseInt(subAdminResDelayDualMode));
			} catch (NumberFormatException | InterruptedException e) {
				LoggerUtils.log(LogType.MODIFY,Level.INFO,"Exception occured while trying to delay the SubAdmin resonse "+e.getClass().getName()+ " : "+e.getMessage(), logClassName);
				LoggerUtils.logException("", e, logClassName);
			}
		} 
		return modified;
	}

	public boolean processModifySecondary(SubscriberState request, SubscriberManager subMgr, Subscriber subscriber) throws SPMLMappingException, AttributeTableException, SOAPDAOException, SPMLOperationFailureException {
		
		boolean modified = false;
		siemens.names.prov.gw.spml._2._0.ModifyRequest spmlRequest = new siemens.names.prov.gw.spml._2._0.ModifyRequest();
		siemens.names.prov.gw.spml._2._0.ModifyResponse spmlResponse = null;
				
		 LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Mapping the request to SPML", logClassName);
		 
		 /** Release 1.2.10 changes - SubAdmin performance improvements
		  * 
		  SubscriberManager subMgr = ldapClient.getSubscriberMgr();
		  Subscriber subscriber = subMgr.get(request.getMSISDNN()); 	 **/
		 
		 SPMLRequestMapper.subadminToSpml(LogType.SUBADMIN,request, spmlRequest, subAdminUtils, subscriber);
		 SoapClient soapClient = soapClientFactory.getClient(ASAPGWProperties.getPropertyValue(SOAP_PROTOCOL));
		 LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Performing SPML modify", logClassName);
		 spmlResponse = soapClient.executeModify(spmlRequest);
		 LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "SPML response obtained", logClassName);
		 
		 /** Release 1.2.10 changes - SubAdmin performance improvements
		 modifyResponse = new ModifyResponse();
		 LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Mapping the SPML response to ASAPGW", logClassName);
		 SPMLResponseMapper.spmlToAsapgw(LogType.MODIFY,spmlResponse, null, modifyResponse);
		 LoggerUtils.log(LogType.SUBADMIN, Level.DEBUG,"Modify response after mapping to asapgw",logClassName);
		 //			LoggerUtils.logFullVfuser(LogType.SUBADMIN, ModifyHandler.class.toString(),modifyResponse.getResultObject().getVfuser());
		 **/
		 
		 if (spmlResponse != null && spmlResponse.getResult()==ResultCode.SUCCESS) {
			 LoggerUtils.log(LogType.SUBADMIN,Level.INFO,"processing of " + request.getMSISDNN() + " is Successfull", logClassName);
			 modified = true;
		 }
		 return modified;
	}

	public boolean processModifyPrimary(SubscriberState request, SubscriberManager subMgr, Subscriber ldapSubsr) throws AttributeTableException {
//		Subscriber s = null;
		String status = "active";	
		boolean modified = false;
//		SubAdminUtils subAdminUtils = new SubAdminUtils();

		if (subAdminUtils.isUnconnectedServiceLevel(request.getServiceLevel())
				|| ServicePropertiesManager.getInstance().isExportedServiceLevel(request.getServiceLevel())) {
			status = "deactivated";
		}

		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "SubscriberManager obtained", logClassName);
		
		/** Release 1.2.10 changes - SubAdmin performance improvements
		 *
		SubscriberManager subMgr = ldapClient.getSubscriberMgr();  **/
		
		Subscriber s = subMgr.get(request.getMSISDNN());                 
		
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Setting WelcomeSmsOptOut", logClassName);
		s.setWelcomeSmsOptOut(false);
		// s.setUid();
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Setting IMSI", logClassName);
		s.setImsi(request.getIMSI());
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Setting ServiceLevel", logClassName);
		s.setServiceLevel("vfuk" + request.getServiceLevel());
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Setting ServiceProviderId", logClassName);
		s.setServiceProviderId(request.getSPCode());

		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "map SP code to SPType", logClassName);
		// map SP code to SPType
		String spType = subAdminUtils.getSpidMap().getAttribute(request.getSPCode(), 1);
		if (spType == null) {
			LoggerUtils.log(LogType.SUBADMIN,Level.ERROR, "spType not found in attribute file", logClassName);
			throw new AttributeTableException(request.getSPCode()
					+ " not found in attribute file "
					+ subAdminUtils.getSpidMap().getFilename());
		}
		s.setServiceProviderType(spType);
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "map SL code to ConnectionType", logClassName);
		// map SL code to ConnectionType
		String connectionType = subAdminUtils.getSlMap().getAttribute(request.getServiceLevel(),
				1);
		if (connectionType == null) {
			throw new AttributeTableException(request.getServiceLevel()
					+ " not found in attribute file " + subAdminUtils.getSlMap().getFilename());
		}
		s.setConnectionType(connectionType);

		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "map SL code to NetworkType", logClassName);
		String smsCapability = subAdminUtils.getSlMap().getAttribute(request.getServiceLevel(), 4);
		s.setSmsCapability(smsCapability);

		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "map SL code to NetworkType", logClassName);
		// map SL code to NetworkType
		String networkType = subAdminUtils.getSlMap().getAttribute(request.getServiceLevel(), 2);
		s.setNetworkType(networkType);
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "subscriber status is set to that previously calculated", logClassName);
		// subscriber status us set to that previously calculated
		s.setSubscriptionStatus(status);
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "map SL code to subscriptionType", logClassName);
		// map SL code to subscriptionType
		String subscriptionType = subAdminUtils.getSlMap().getAttribute(
				request.getServiceLevel(), 3);
		if ("unconnected".equalsIgnoreCase(subscriptionType)) {
			s.setSubscriptionType("other");
		} else {
			s.setSubscriptionType(subscriptionType);
		}

		ArrayList l = new ArrayList(s.getSubAdminBars());

		// profiles_cur.adda
		for (int i = 0; i < l.size(); i++) {
			s.removeSubAdminBar((String) l.get(i));
		}
		l = new ArrayList(s.getSubAdminSubscriptions());
		for (int i = 0; i < l.size(); i++) {
			s.removeSubAdminSubscription((String) l.get(i));
		}
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Remove Data Bars and subscriptions and re-add them from profiles", logClassName);
		// Remove Data Bars and subscriptions and re-add them from profiles
		l = new ArrayList(s.getSubAdminDataBars());
		for (int i = 0; i < l.size(); i++) {
			s.removeSubAdminDataBar((String) l.get(i));
		}
		l = new ArrayList(s.getSubAdminDataSubscriptions());
		for (int i = 0; i < l.size(); i++) {
			s.removeSubAdminDataSubscription((String) l.get(i));
		}
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Add Bars and Features from Suffixes", logClassName);
		// Add Bars and Features from Suffixes
		String suffixList = request.getSuffixList();
		if (suffixList == null) {
			suffixList = "";
		}
		suffixList = suffixList.trim();

		for (int idx = 0; idx < suffixList.length(); idx++) {
			String suffix = suffixList.substring(idx, idx + 1);
			String roleType = subAdminUtils.getSuffixMap().getAttribute(suffix, 1);
			if (roleType == null) {
				// error unknown suffix encountered
				LoggerUtils.log(LogType.SUBADMIN,Level.ERROR, "unknown suffix encountered", logClassName);
				throw new AttributeTableException("suffix '" + suffix
						+ "' not present in " + subAdminUtils.getSuffixMap().getFilename());
			}
			String val = subAdminUtils.getSuffixMap().getAttribute(suffix, 2);

			if ("Bar".equalsIgnoreCase(roleType)) {
				s.addSubAdminBar(val);
			} else if ("Feature".equalsIgnoreCase(roleType)) {
				s.addSubAdminSubscription(val);
			} else if (!"none".equalsIgnoreCase(roleType)) {
				throw new AttributeTableException(
						"suffix must be Bar, Feature or none. Got "
								+ roleType + " instead.");
			}

		}
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, " Add Bars and Features from profiles", logClassName);
		// Add Bars and Features from profiles
		String[] profiles = request.getProfile();
		String profile = null;
		for (int idx = 0; idx < profiles.length; idx++) {
			profile = profiles[idx].trim();
			String roleType = subAdminUtils.getProfileMap().getAttribute(profile, 1);
			if (roleType == null) {
				// error unknown profile encountered
				LoggerUtils.log(LogType.SUBADMIN,Level.ERROR, " unknown profile encountered", logClassName);
				throw new AttributeTableException("profile '" + profile
						+ "' not in " + subAdminUtils.getProfileMap().getFilename());
			}
			String val = subAdminUtils.getProfileMap().getAttribute(profile, 2);
			if ("Bar".equalsIgnoreCase(roleType)) {
				s.addSubAdminBar(val);
			} else if ("Feature".equalsIgnoreCase(roleType)) {
				s.addSubAdminSubscription(val);
			} else if ("DataBar".equalsIgnoreCase(roleType)) {
				s.addSubAdminDataBar(val);
			} else if ("DataFeature".equalsIgnoreCase(roleType)) {
				s.addSubAdminDataSubscription(val);
			} else if (!"none".equalsIgnoreCase(roleType)) {
				LoggerUtils.log(LogType.SUBADMIN,Level.ERROR, " profile must be Bar, Feature, DataBar, DataFeature or none", logClassName);
				throw new AttributeTableException(
						"profile must be Bar, Feature, DataBar, DataFeature or none. Got "
								+ roleType + " instead.");
			}

		}

		s.setSim(request.getSIMSerialNo());
		LoggerUtils.log(LogType.SUBADMIN,Level.DEBUG, "Write to CUR DB", logClassName);
		// Write to CUR DB
		subMgr.commit(s);
		modified = true;
		return modified;
	}
}
