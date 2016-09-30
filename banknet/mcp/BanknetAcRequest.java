/*
 * Copyright (c) 2010-2011 AEON Credit Technology, Inc. All Rights Reserved.
 * 
 * This software is the confidential and proprietary information of AEON
 * Credit Technology Systems, Phil. Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with AEON Credit Technology.
 *
 * AEON MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING
 *
 * 
 * DATE          AUTHOR          REMARKS / COMMENT
 * ====----------======----------=================------------------------------
 * Jul-28-2010   fsamson         Major code update
 * Jun-09-2010   iburgos         Initial code.
 * Aug-12-2010   lquirona        Major code update
 * Sept-22-2010  lquirona        Changes in network key/added methods
 * 
 */
package com.aeoncredit.fep.core.adapter.brand.banknet.mcp;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericValidatingPackager;

import static com.aeoncredit.fep.core.adapter.brand.common.Constants.*;

import com.aeoncredit.fep.common.SysCode;
import com.aeoncredit.fep.core.adapter.brand.common.Constants;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.adapter.brand.common.Keys;
import com.aeoncredit.fep.core.adapter.inhouse.hsmthales.utility.commandmessage.HSMCommandCA;
import com.aeoncredit.fep.core.adapter.inhouse.hsmthales.utility.responsemessage.HSMResponseCB;
import com.aeoncredit.fep.core.internalmessage.FepMessage;
import com.aeoncredit.fep.core.internalmessage.InternalFormat;
import com.aeoncredit.fep.core.internalmessage.keys.BKNMDSFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.IInternalKey;
import com.aeoncredit.fep.core.internalmessage.keys.BKNMDSFieldKey.BANKNET_MDS;
import com.aeoncredit.fep.core.internalmessage.keys.EDCShoppingFieldKey.EDC_SHOPPING;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.CONTROL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_COMMON_DATA;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_FEP_ADDITIONAL_INFO;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_HEADER;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.IC_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.OPTIONAL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey;
import com.aeoncredit.fep.framework.dbaccess.FEPT015BnkaqDAO;
import com.aeoncredit.fep.framework.dbaccess.FEPT015BnkaqDomain;
import com.aeoncredit.fep.framework.dbaccess.FEPT084EdcmsttblDAO;
import com.aeoncredit.fep.framework.dbaccess.FEPT084EdcmsttblDomain;
import com.aeoncredit.fep.framework.dbaccess.FEPTransactionMgr;
import com.aeoncredit.fep.framework.exception.AeonException;
import com.aeoncredit.fep.framework.exception.DBNOTInitializedException;
import com.aeoncredit.fep.framework.exception.HSMException;
import com.aeoncredit.fep.framework.exception.IllegalParamException;
import com.aeoncredit.fep.framework.exception.SharedMemoryException;
import com.aeoncredit.fep.framework.hsmaccess.HSMThalesAccess;
import com.aeoncredit.fep.framework.log.LogOutputUtility;
import com.aeoncredit.fep.framework.mcp.IMCPProcess;
// [salvaro:06/28/2012]: [Redmine#2553] Remove unused imports
//import com.aeoncredit.fep.framework.util.MaskUtil;
//import com.aeoncredit.fep.framework.util.XmlConsts;

/**
 * Execute the Banknet Acquiring request
 * @author iburgos
 *
 */
public class BanknetAcRequest extends BanknetCommon{

	private FEPT015BnkaqDAO dao;
	private BanknetInternalFormatProcess messageConverter;
	private FEPTransactionMgr transactionMgr;
	private FEPT015BnkaqDomain domain;	
    private InternalFormat internalFormat;
	private ISOMsg isoMsg;
	private String stanForInserting;
	
	/** Variables used for Exception Handling*/
	private String[] args   = new String[2];
	private String sysMsgID  = NO_SPACE;
    
    // [emaranan:02/21/2011] [Redmine#2230]
    private FEPT084EdcmsttblDomain checkDomain;
    private FEPT084EdcmsttblDAO checkDAO;
    
    /**
	 * Constructor
	 * @param mcpProcess The instance of AeonProcessImpl
	 */
	public BanknetAcRequest(IMCPProcess mcpProcess) {
		super(mcpProcess);
		try {
			dao = new FEPT015BnkaqDAO(mcpProcess.getDBTransactionMgr());
			domain = new FEPT015BnkaqDomain();
			messageConverter = new BanknetInternalFormatProcess(mcpProcess);
			transactionMgr = mcpProcess.getDBTransactionMgr();
			packager = new GenericValidatingPackager(getPackageConfigurationFile());
            
            //[emaranan:02/21/2011] [Redmine#2230]
            checkDAO = new FEPT084EdcmsttblDAO(mcpProcess.getDBTransactionMgr());
		} catch (DBNOTInitializedException dnie) {
			args[0]  = dnie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(dnie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest]: End Process");
			return;
		} catch (ISOException ie){
			args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
			return;
		}// End of try catch
	}// End of constructor
	
	/**
	 * Business logic of the Base1AcRequest class.
	 * Parses a message that has InternalFormat as its message contents and reply with
	 * a FepMessage which contains ISOMsg.
	 * @param msg The FepMessage received which contains Internal Format
	 */
	public void execute(FepMessage fepMessage) {
		String queueName;
		String acquiringLogKey;
		String nwKeySearchingOriginal;
		List<FEPT015BnkaqDomain> resultList = null;
		FEPT015BnkaqDomain recordTemp = null;
		byte[] byteArray;
		stanForInserting = Constants.NO_SPACE;
		
        // [lsosuan:01/31/2011] [Redmine#2184] [LogSequence]
        // Set the LogSequence
        mcpProcess.setLogSequence(fepMessage.getLogSequence()); 
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
        		"********** BRAND MCP ACQUIRING REQUEST START PROCESS (Seq="+ fepMessage.getLogSequence() +")**********");
        
		/*
         * Check Merchant ID and Terminal ID by method checkMerchantIDANDTerminalID()                    
         *  If the return value is false                
         *   Send the decline message to the FEP processing function.            
         *       Processing type identifier = "22"       
         *       Data format identifier = "01"       
         *   End the process.
         *   
         * [maguila:03/18/2011] [Redmine#2230] [Implemented DD Update]
		 */
		internalFormat = fepMessage.getMessageContent();
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "received a message from <"+ 
			     mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
			"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
			"| Data format:" + fepMessage.getDataFormatIdentifier() +
			"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
			"| FepSeq:" + fepMessage.getFEPSeq() +
			"| NetworkID: BNK" + 
			"| Transaction Code(service):" + internalFormat.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
			"| Auth ID Response from HOST:" + internalFormat.getValue(HOST_COMMON_DATA.AUTHORIZATION_ID_RESPONSE).getValue() + 
			"| SourceID:" + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
			"| DestinationId:" + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() + 
			"| FEP response Code:" + internalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		
        // Get the Source ID
		String sourceId = internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue();
		
		if (SysCode.NETWORK_EDC.equals(sourceId)) {
            if (checkMerchantIDANDTerminalID(fepMessage) == false) {
                fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
                sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
						+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
						"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + fepMessage.getDataFormatIdentifier() +
						"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + fepMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + internalFormat.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalFormat.getValue(
								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
            } // End of inner IF statement
        } // End of outer IF statement
		
		// Get the message content
		internalFormat = fepMessage.getMessageContent();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetAcRequest] : execute() : internal Format");
		
		// Retrieve transaction code
		String transactionCode = internalFormat.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue();
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : transactionCode : "
                        + transactionCode.substring(0, 2));
        
		// If it is the Reversal Advice(Transaction Code = "04XXXX")
		if (SysCode.MSGTYPE_REV_ADVICE.equals(transactionCode.substring(0, 2))) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : Reversal Advice");
			
			FepMessage newFepMessage = null;
			// Edit the ISOMsg.
			try {
				newFepMessage = editMsg(fepMessage);
			} catch (Exception e) {
				/*
				 * If error occurs,
				 *  Output the System Log. IMCPProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, "CSW2131")
				 *  Edit the approve internal format message.
				 * Transmit to the FEP Processing Function.
				 *  Edit the queue header.
				 *   Processing type identifier = "22" 
				 *   Data format identifier = "01"
				 *  Call sendMessage(String queueName, FepMessage message)
				 *   to send the message to the queue of
				 *   FEP processing function.
				 *   queueName=mcpProcess.getQueueName(SysCode.LN_FEP_MCP),refer to 
				 *   "SS0111 System Code Definition(FEP).xls"
				 *  End the process
				 */
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131); 
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131, null, null, e);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : editMsg fail: " 
	                    + " Exception occurred ---" + FepUtilities.getCustomStackTrace(e));
				// [maguila 03/17/2011 Redmine Issue # 2258 Edit the approve internal format message.]
				internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, Constants.FEP_BNK_RESPONSE_CODE_APPROVED);
                internalFormat.setValue(HOST_COMMON_DATA.RESPONSE_CODE,	FLD39_RESPONSE_CODE_SUCCESS);
				fepMessage.setMessageContent(internalFormat);
				fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
				fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
                sendMessage(queueName, fepMessage);
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e));
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
						+ queueName + ">" +
						"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + fepMessage.getDataFormatIdentifier() +
						"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + fepMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + internalFormat.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
				return;
			} // End of editMsg try-catch
			
			/*
			 * Search BANKNET Acquiring Log Table to get original message.
			 * Message Type ID=MTI (original transaction, so mti should not be 0420)
			 * Call getNetWorkKeySearchingOriginal() method to get the key for searching original message
			 */
			acquiringLogKey = NO_SPACE;			
			acquiringLogKey = getNetworkKeySearchingOriginal(fepMessage);
		
			// Call FEPT015BnkaqDAO.findByCondition() to search the message
			domain = new FEPT015BnkaqDomain();
			domain.setNw_key_searching_original(acquiringLogKey);
			
			try {
				resultList = dao.findByCondition(domain);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : resultList : " + resultList);
			} catch (Exception e) {
				args[0]  = e.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
				return;
			}
			
			// If there is no original transaction,
			if(null == resultList || resultList.isEmpty()){
				
				/*
				 * [maguila 03/17/2011 Redmine Issue # 2258]
				 * Output the System Log.IMCPProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "CSW2135")
				 * Send the approve message to the FEP processing function
				 */
				mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, CLIENT_SYSLOG_SCREEN_2135);
				internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, Constants.FEP_BNK_RESPONSE_CODE_APPROVED);
				fepMessage.setMessageContent(internalFormat);
                fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
				
				sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] : execute() : resultList is null or empty. ");
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
						+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
						"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + fepMessage.getDataFormatIdentifier() +
						"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + fepMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + internalFormat.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
				// End the process.
				return;
			}
			
			/* [ Lquirona 03/24/2011 - Redmine Issue #2297 
			 * - Error occurred because the method for setting fields for 0400 (setOriginalTransactionValues) was not called.]
			 * 
			 * If record =1
			 */
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : record = 1 ");
			recordTemp = new FEPT015BnkaqDomain();
			recordTemp = (FEPT015BnkaqDomain)resultList.get(0);
			
			/* 
			 * Update the reversal message with values from the original message and 
			 *  use the returned byte array when inserting to the DB
			 */
			try {				
				// Original Request Message
				byte[] originalReqByte = recordTemp.getMsg_data_request_iso();
				ISOMsg originalReqIsoMsg = new ISOMsg();
				originalReqIsoMsg.setPackager(packager);
				originalReqIsoMsg.unpack(originalReqByte);
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest]  : execute() : " +
						"Original Request ISO : " + originalReqIsoMsg);
			
				// Original Response Message
				byte[] originalResByte = recordTemp.getMsg_data();
				ISOMsg originalResIsoMsg = new ISOMsg();
				originalResIsoMsg.setPackager(packager);
				originalResIsoMsg.unpack(originalResByte);
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : " +
						"Original Response ISO : " + originalResIsoMsg);
									
				byteArray = this.setOriginalTransactionValues(originalReqIsoMsg, 
						originalResIsoMsg, (byte[])newFepMessage.getMessageContent());
				newFepMessage.setMessageContent(byteArray);
				
			} catch (Throwable th) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(th));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "Error in getting Msg_data object");
				return;
			}
			
			/*
			 * Insert BANKNET Acquiring Log Table
			 * Call getNetWorkKeyForInserting() method to get the key which will 
			 * be inserted into Item "Net Work Key" of Table
			 * Item "Net Work Key Searching Original" could be null here
			 */
			acquiringLogKey = NO_SPACE;
			try {
				acquiringLogKey = getNetworkKeyForInserting(fepMessage);
				
			} catch (IllegalParamException ipe) {
				args[0]  = ipe.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
	            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] : execute() : End Process");
	            return;
	            
			} catch (SharedMemoryException sme) {
				args[0]  = sme.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2020);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2020, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(sme));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] : execute() : End Process");
				return;
				
			}  catch (ParseException pe) {
				args[0] = pe.getMessage();
	            sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_ILLEGAL_PARAM_2100);
	            mcpProcess.writeAppLog(sysMsgID,LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_ILLEGAL_PARAM_2100, args, null, pe);
	            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(pe));
	            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] : execute() : End Process");
	            return;
	            
			} catch (Exception ex) {
				args[1] = ex.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, 
						Constants.CLIENT_SYSLOG_DB_7007);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args, null, ex);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(ex));
				
				try {
					transactionMgr.rollback();
					
				} catch (SQLException sqlEx) {
					mcpProcess.error("Error in rollback ");
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sqlEx));
				}
				return;
			} // End of getNetworkKeyForInserting try-catch
			
			try {
				domain.setNw_key(acquiringLogKey);
				/*
				 * [mquines:20110126] Removed
				 * [Redmine Issue 2297 - no need for setting setNw_key_searching_original for rev/adv
				 * domain.setNw_key_searching_original(getNetworkKeySearchingOriginalForInserting(fepMessage));
				 */
				domain.setBus_date(fepMessage.getDateTime().substring(0, 8));
				domain.setFep_seq(new BigDecimal(fepMessage.getFEPSeq()));
				domain.setMsg_data_request_internal(FepUtilities.getBytes(internalFormat)); 
				domain.setPro_status(COL_PROCESS_STATUS_APPROVED);
				domain.setPro_result(COL_PROCESS_RESULT_APPROVE);
				domain.setUpdateId(mcpProcess.getProcessName());
				
				//[mquines:20110126] Added
				domain.setMsg_data(FepUtilities.getBytes(internalFormat));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
						" Nw_key :" + domain.getNw_key());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
						" Nw_key_searching_original: " + domain.getNw_key_searching_original());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
						" Bus_date :" + domain.getBus_date());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
						"Fep_seq: " + domain.getFep_seq());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
						" Pro_status: " + domain.getPro_status());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
						" Pro_result: " + domain.getPro_result());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
						" UpdateId: " + domain.getUpdateId());
				
				try {
					dao.insert(domain);
					transactionMgr.commit();
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetAcRequest]  : execute() : domain inserted for reversal advice ");
					
				} catch (Exception e) {
					//[MQuines:12/21/2011] Modified the exception handling
					try {
						transactionMgr.rollback();
			 			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] Rollback Occurs");
			 		 
			 		} catch (SQLException se) {  			 
						 args[0]  = se.getMessage();
						 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154);
						 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154, args);
						 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(se));
						 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] Nothing to Rollback");
			 		 } // End of try catch
			         
					 args[0]  = e.getMessage();
					 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007);
					 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args);
					 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
					 
					 mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, CLIENT_SYSLOG_SCREEN_2136);
					 internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_BNK_RESPONSE_CODE_APPROVED);
					 fepMessage.setMessageContent(internalFormat);
					 fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
					 fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
					 sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
						
					 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
							+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
							"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
							"| Data format:" + fepMessage.getDataFormatIdentifier() +
							"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
							"| FepSeq:" + fepMessage.getFEPSeq() +
							"| NetworkID: BNK" + 
							"| Transaction Code(service):" + internalFormat.getValue(
									HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
							"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
							"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
							"| Response Code: "+ internalFormat.getValue(
									CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
					 
					 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
					 return;
				}// End of try catch	
				
			} catch (Throwable th) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(th));
				return;
			} // End of insert try-catch
			
			/*
			 * Edit the queue header.
			 *  Processing type identifier = "05"
			 *  Data format identifier = "10"
			 *  
			 * Call sendMessage(String queueName, FepMessage message) to send
			 *  the message to the queue of BANKNET STORE process.
			 */
			newFepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			newFepMessage.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);
			
			queueName = mcpProcess.getQueueName(SysCode.LN_BNK_STORE);
			sendMessage(queueName, newFepMessage);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
					+ queueName + ">" +
					"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMessage.getDataFormatIdentifier() +
					"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMessage.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormat.getValue(
							HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
				
		
		} else { //If not reversal advice
			
			// Check the network status and line status.
			int networkStatus = getNetworkStatus();
			int lineStatus = getLineStatus();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : networkStatus = " + networkStatus 
					+ " lineStatus = " + lineStatus);
			
			/*
			 * If sign-off or line status is 0(LINE_NG),
			 * Transmit to the FEP Processing Function.
			 *  Processing type identifier = "22" 
			 *  Data format identifier = "01"
			 * Call sendMessage(String queueName, FepMessage message) to 
			 * send the message to the queue of FEP processing function.
			 */
			if ((SysCode.SIGN_OFF == networkStatus) || (SysCode.LINE_NG == lineStatus)) {
                // [maguila 03/17/2011 Redmine Issue # 2258 Edit the decline internal format message.]
				internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, SysCode.FEP_RES_CD_BKN_LIN_ERR);
				
				fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
				fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE); 
				
				fepMessage.setMessageContent(internalFormat);
				sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
						+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
						"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + fepMessage.getDataFormatIdentifier() +
						"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + fepMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + internalFormat.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalFormat.getValue(
								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			
			/*
			 * If sign-on and line status is 1(LINE_OK),
			 *  Edit the ISOMsg.
			 *  Output the System Log. IMCPProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, "CSW2131")  
             * If the transaction code = "03xxxx" .
             *     Edit the approve internal format message.
             * Else
			 *     Edit the decline internal format message.
			 * Transmit to the FEP Processing Function.
			 *  Edit the queue header.
			 *  Processing type identifier = "22" 
			 *  Data format identifier = "01"
			 * End the process
			 */
			} else if ((SysCode.SIGN_ON == networkStatus) && (SysCode.LINE_OK == lineStatus)) {
				FepMessage newFepMessage = null;
				
				try {
					newFepMessage = editMsg(fepMessage);
				} catch (Exception e) {//If error occurs,
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131); 
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131, null, null, e);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"## [BanknetAcRequest] editMsg execute() Fail: " 
		                    + " Exception occurred ---" + FepUtilities.getCustomStackTrace(e));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e));
					/*
					 * [maguila 03/17/2011 Redmine Issue # 2258]
					 * If the transaction code = "03xxxx".
					 *  Edit the approve internal format message.
					 * Else 
					 *  Edit the decline internal format message.
					 */
					if(SysCode.MSGTYPE_REV_REQ.equals(transactionCode.substring(0, 2))) {
						internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, 
                                Constants.FEP_BNK_RESPONSE_CODE_APPROVED);
                    } else {
                        internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, 
                                SysCode.FEP_RES_CD_BKN_REQ_MSG_ERR);
                    }
					fepMessage.setMessageContent(internalFormat);
					fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
					fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

                    queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
    				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
    						+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
    						"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
    						"| Data format:" + fepMessage.getDataFormatIdentifier() +
    						"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
    						"| FepSeq:" + fepMessage.getFEPSeq() +
    						"| NetworkID: BNK" + 
    						"| Transaction Code(service):" + internalFormat.getValue(
    								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
    						"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
    						"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
    						"| FEP Response Code: "+ internalFormat.getValue(
    								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
                    
					sendMessage(queueName, fepMessage);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] execute():exit");
					return;
				}  // End of editMsg try-catch
				
				/*
				 * Processing for table
				 * If Reversal request message ("03XXXX")
				 *  Call getNetWorkKeySearchingOriginal() method to get the key for searching original message
				 *  Call FEPT015BnkaqDAO.findByCondition() to search the message
				 */
				if (SysCode.MSGTYPE_REV_REQ.equals(transactionCode.substring(0, 2))) {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : Reversal Request ");
					acquiringLogKey = NO_SPACE;
					
					acquiringLogKey = getNetworkKeySearchingOriginal(fepMessage);
		
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
								"[BanknetAcRequest] : execute() : NetworkKeySearchingOriginal : " + acquiringLogKey);
					
					domain = new FEPT015BnkaqDomain();
					domain.setNw_key_searching_original(acquiringLogKey);
					resultList = null;
					try {
						// resultList contains the original message
						resultList = dao.findByCondition(domain);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : resultList : " + resultList);
					} catch (Exception e) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] : " +
						"Error in finding original transaction (reversal request). ");
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e));
					}
					
					// If record=0
					if(resultList == null || resultList.isEmpty()){
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : record = 0 ");
						
						/*
						 * [maguila 03/17/2011 Redmine Issue # 2258]
						 * Output the System Log. IMCPProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "CSW2135")
						 * Send the approve message to the FEP processing function.  
						 */
						mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, CLIENT_SYSLOG_SCREEN_2135);
						internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, 
                                Constants.FEP_BNK_RESPONSE_CODE_APPROVED);
						fepMessage.setMessageContent(internalFormat);
                        fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                        fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
						sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR,  "[BanknetAcRequest] : Error:resultList is null or empty.");
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
								+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
								"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
								"| Data format:" + fepMessage.getDataFormatIdentifier() +
								"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
								"| FepSeq:" + fepMessage.getFEPSeq() +
								"| NetworkID: BNK" + 
								"| Transaction Code(service):" + internalFormat.getValue(
										HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
								"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
								"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
								"| FEP Response Code: "+ internalFormat.getValue(
										CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
						// End the process.
						return;
					} // End of record = 0
					

					/*
					 * If record =1
					 * Update the reversal message with values from the original message and 
					 *  use the returned byte array when inserting to the DB
					 */
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : record = 1 ");
					recordTemp = new FEPT015BnkaqDomain();
					recordTemp = (FEPT015BnkaqDomain)resultList.get(0);
					
					try {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "get Msg_data object ");
						
						// Original Request Message
						byte[] originalReqByte = recordTemp.getMsg_data_request_iso();
						ISOMsg originalReqIsoMsg = new ISOMsg();
						originalReqIsoMsg.setPackager(packager);
						originalReqIsoMsg.unpack(originalReqByte);
						
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest]  : execute() : " +
								"Original Request ISO : " + originalReqIsoMsg);
					
						// Original Response Message
						byte[] originalResByte = recordTemp.getMsg_data();
						ISOMsg originalResIsoMsg = new ISOMsg();
						originalResIsoMsg.setPackager(packager);
						originalResIsoMsg.unpack(originalResByte);
						
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest]  : execute() : " +
								"Original Response ISO : " + originalResIsoMsg);
											
						byteArray = this.setOriginalTransactionValues(originalReqIsoMsg, 
								originalResIsoMsg, (byte[])newFepMessage.getMessageContent());
						newFepMessage.setMessageContent(byteArray);
						
					} catch (Throwable th) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(th));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "Error in getting Msg_data object");
						return;
					}
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : status = " + recordTemp.getPro_status());
					
					/*
					 * If status="2"( Approve)
					 *  Call FEPT015BnkaqDAO.update() to update the status of
					 * the record to "4"(Reversal in progress) , then enter
					 * into step3 of following
					 */
					if (recordTemp.getPro_status().equals(COL_PROCESS_STATUS_APPROVED)) {
						domain = new FEPT015BnkaqDomain();
						domain.setNw_key(recordTemp.getNw_key());
						domain.setNw_key_searching_original(recordTemp.getNw_key_searching_original());
						domain.setMsg_data(recordTemp.getMsg_data());
						domain.setPro_status(COL_PROCESS_STATUS_REVERSAL_IN_PROGRESS);
						domain.setPro_result(recordTemp.getPro_result());
						domain.setFep_seq(recordTemp.getFep_seq());
						domain.setBus_date(recordTemp.getBus_date());
						domain.setUpdateId(mcpProcess.getProcessName());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "UPDATING...");
						
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Nw_key :" + domain.getNw_key());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Nw_key_searching_original: " + domain.getNw_key_searching_original());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Bus_date :" + domain.getBus_date());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								"Fep_seq: " + domain.getFep_seq());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Pro_status: " + domain.getPro_status());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" UpdateId: " + domain.getUpdateId());
						
						//[MQuines:12/21/2011] Added try catch
						try {
							dao.update(domain);
							transactionMgr.commit();
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									"domain updated for reversal request ");
						} catch (Exception e) {
							try {
								transactionMgr.rollback();
					 			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] Rollback Occurs");
					 		 
					 		} catch (SQLException se) {  			 
								 args[0]  = se.getMessage();
								 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154);
								 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154, args);
								 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(se));
								 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] Nothing to Rollback");
					 		 } // End of try catch
					         
							 args[0]  = e.getMessage();
							 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007);
							 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args);
							 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
							 
							 mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, CLIENT_SYSLOG_SCREEN_2136);
							 internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_BNK_RESPONSE_CODE_APPROVED);
							 fepMessage.setMessageContent(internalFormat);
							 fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
							 fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
							 sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
								
							 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
									+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
									"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
									"| Data format:" + fepMessage.getDataFormatIdentifier() +
									"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
									"| FepSeq:" + fepMessage.getFEPSeq() +
									"| NetworkID: BNK" + 
									"| Transaction Code(service):" + internalFormat.getValue(
											HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
									"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
									"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
									"| Response Code: "+ internalFormat.getValue(
											CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
							 
							 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
							 
							 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
                			return;
                			
						}// End of try-catch
						
						/*
						 * Call getNetWorkKeyForInserting()
						 *  method to get the key which will be inserted into 
						 *  Item "Net Work Key" of Table
						 *  Item "Net Work Key Searching Original" could be null here
						 */
						acquiringLogKey = NO_SPACE;
						
						try {
							acquiringLogKey = getNetworkKeyForInserting(fepMessage);
							
						} catch (IllegalParamException ipe) {
							args[0]  = ipe.getMessage();
							sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
							mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
				            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
							return;
				            
						} catch (SharedMemoryException sme) {
							args[0]  = sme.getMessage();
							sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2020);
							mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2020, args);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(sme));
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
							return;
				            
						}  catch (ParseException pe) {
							args[0] = pe.getMessage();
				            sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_ILLEGAL_PARAM_2100);
				            mcpProcess.writeAppLog(sysMsgID,LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_ILLEGAL_PARAM_2100, args);
				            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(pe));
				            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
							return;
							
						} catch (Exception ex) {
							args[0] = ex.getMessage();
							sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,	CLIENT_SYSLOG_DB_7007);
							mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
							
							try {
								transactionMgr.rollback();
								
							} catch (SQLException sqlEx) {
								mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
								FepUtilities.getCustomStackTrace(sqlEx));
							}
							
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
							return;
						} // End of getNetworkKeyForInserting try-catch
						
						try {
                            domain = new FEPT015BnkaqDomain();
                            /*
                             * [mquines:01/26/2011] Temp fix for Redmine Issue #2167
                             * String nw_key_rev = recordTemp.getNw_key(). replaceFirst("01", "04");
                             * 
                             * [mquines:01/26/2011] Must have value
                             * [emaranan:02/28/2011] Redmine 2167 
                             * Change by changing key from 0100 request to acquiring log key.
                             */ 
							domain.setNw_key(acquiringLogKey);
							
                            /*
                             * [emaranan:03/18/2011] Redmine 2258 Not needed for Reversal transactions.
                             * domain.setNw_key_searching_original(getNetworkKeySearchingOriginalForInserting(fepMessage));
                             */
							domain.setBus_date(fepMessage.getDateTime().substring(0, 8));
							domain.setFep_seq(new BigDecimal(fepMessage.getFEPSeq()));
							
                            // [emaranan:03/18/2011] Redmine 2258 To be retrive by voide reversal
                            domain.setMsg_data_request_iso((byte[])newFepMessage.getMessageContent());
							domain.setMsg_data(FepUtilities.getBytes(internalFormat)); 
							domain.setPro_status(COL_PROCESS_STATUS_APPROVED);
							domain.setPro_result(COL_PROCESS_RESULT_APPROVE);
							domain.setUpdateId(mcpProcess.getProcessName());
							
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : Insert");
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									" Nw_key :" + domain.getNw_key());
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									" Nw_key_searching_original: " + domain.getNw_key_searching_original());
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									" Bus_date :" + domain.getBus_date());
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									"Fep_seq: " + domain.getFep_seq());
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									" Pro_status: " + domain.getPro_status());
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									" Pro_result: " + domain.getPro_result());
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									" UpdateId: " + domain.getUpdateId());
							
							try {
								dao.insert(domain);
								transactionMgr.commit();
								mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : " +
								"domain inserted for reversal request ");
								
							} catch (Exception e) {
								// [MQuines:12/21/2011] Modified the exception handling
								try {
									transactionMgr.rollback();
						 			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] Rollback Occurs");
						 		 
						 		} catch (SQLException se) {  			 
									 args[0]  = se.getMessage();
									 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154);
									 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154, args);
									 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(se));
									 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] Nothing to Rollback");
						 		 } // End of try catch
						         
								 args[0]  = e.getMessage();
								 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007);
								 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args);
								 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
								 
								 mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, CLIENT_SYSLOG_SCREEN_2136);
								 internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_BNK_RESPONSE_CODE_APPROVED);
								 fepMessage.setMessageContent(internalFormat);
								 fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
								 fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
								 sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
									
								 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
										+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
										"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
										"| Data format:" + fepMessage.getDataFormatIdentifier() +
										"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
										"| FepSeq:" + fepMessage.getFEPSeq() +
										"| NetworkID: BNK" + 
										"| Transaction Code(service):" + internalFormat.getValue(
												HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
										"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
										"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
										"| Response Code: "+ internalFormat.getValue(
												CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
								 
								 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
								 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
								return;
							}// End of try catch	
							
						} catch (Throwable th) {
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							FepUtilities.getCustomStackTrace(th));
							return;
						} // End of insert try-catch
					// End of If status="2"( Approve)
						
					} else {
						/* If status is others
						 * 
						 * [maguila 03/17/2011 Redmine Issue # 2258]
						 * Output the System Log. IMCPProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "CSW2136")
						 * Send the approve message to the FEP processing function.
						 */
						mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, CLIENT_SYSLOG_SCREEN_2136);
						internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_BNK_RESPONSE_CODE_APPROVED);
						fepMessage.setMessageContent(internalFormat);
						fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
						fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
						sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
						
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] : execute() : Status not equal to 2 ");
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
								+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
								"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
								"| Data format:" + fepMessage.getDataFormatIdentifier() +
								"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
								"| FepSeq:" + fepMessage.getFEPSeq() +
								"| NetworkID: BNK" + 
								"| Transaction Code(service):" + internalFormat.getValue(
										HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
								"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
								"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
								"| Response Code: "+ internalFormat.getValue(
										CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
						// End the process.
						return;
					} // End of status is others
					
				/*
				 * End If AuthorizationMsg_data request message ("01XXXX")
				 * 
				 * [emaranan:02/24/2011] [Redmine#2228] [ACQ] SS documents update   
				 */
				} else if (PREFIX_TRANS_CODE_AUTHORIZATION_REQ_1.equals(transactionCode.substring(0, 2)) 
                        || PREFIX_TRANS_CODE_AUTHORIZATION_REQ_4.equals(transactionCode.substring(0, 2))){ 
					/*
					 * If Authorization request message
					 *  Insert BANKNET Acquiring Log Table.
					 *   Call getNetWorkKeyForInserting() method to get the key which will be 
                     *    inserted into Item "Net Work Key" of Table
                     *   Call getNetWorkKeySearchingOriginalForInserting() method to get the key 
                     *    which will be inserted into item "Net Work Key Searching Original" of Table
					 */
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : " +
							"Authorization request ");
					 
					acquiringLogKey = NO_SPACE;
					
					try {
                        acquiringLogKey = getNetworkKeyForInserting(fepMessage);
                        
                        // Redmine Issue 2165
                        nwKeySearchingOriginal = getNetworkKeySearchingOriginalForInserting(fepMessage); 
                        
                        /*
						 * [fsamson:08/17/2010] [Redmine#905]
						 *  [FEP Sequence was modified in BANKNET MCP]
						 * String newFepSequence = mcpProcess.getSequence(FEP_SEQUENCE);
						 */
						domain = new FEPT015BnkaqDomain();
						domain.setNw_key(acquiringLogKey);
						domain.setNw_key_searching_original(nwKeySearchingOriginal);
						domain.setBus_date(fepMessage.getDateTime().substring(0, 8));
						domain.setFep_seq(new BigDecimal(fepMessage.getFEPSeq()));
						domain.setMsg_data(FepUtilities.getBytes(internalFormat)); 
						domain.setPro_status(COL_PROCESS_STATUS_IN_PROGRESS);
						
		                // [mquines:09162010] [Redmine#1620] [The default value of PRO_RESULT "1"(Decline)]
	                    domain.setPro_result(COL_PROCESS_RESULT_DECLINE);
	  					domain.setUpdateId(mcpProcess.getProcessName());
	  					  					
	  					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() : Insert");
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Nw_key :" + domain.getNw_key());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Nw_key_searching_original: " + domain.getNw_key_searching_original());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Bus_date :" + domain.getBus_date());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								"Fep_seq: " + domain.getFep_seq());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Pro_status: " + domain.getPro_status());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" Pro_result: " + domain.getPro_result());
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
								" UpdateId: " + domain.getUpdateId());
						
						try {
							/*
                             * [jfabian:11/05/2010] [Redmine#1767] [Insert Internal Format into item 
                             *  "Message Data Request Message Internal Format" of table]
                             */
                            domain.setMsg_data_request_internal(FepUtilities.getBytes(internalFormat));
                            domain.setMsg_data_request_iso((byte[])newFepMessage.getMessageContent());
							dao.insert(domain);
							transactionMgr.commit();
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : execute() :" +
									" domain inserted for auth request ");
							
						} catch (Exception e) {
							//[MQuines:12/21/2011] Modified the exception handling
							try {
								transactionMgr.rollback();
					 			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] Rollback Occurs");
					 		 
					 		} catch (SQLException se) {  			 
								 args[0]  = se.getMessage();
								 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154);
								 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154, args);
								 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(se));
								 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] Nothing to Rollback");
					 		 } // End of try catch
					         
							 args[0]  = e.getMessage();
							 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007);
							 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args);
							 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
							 mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, CLIENT_SYSLOG_SCREEN_2136);
							 internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_BNK_RESPONSE_CODE_APPROVED);
							 fepMessage.setMessageContent(internalFormat);
							 fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
							 fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
							 sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMessage);
								
							 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
									+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
									"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
									"| Data format:" + fepMessage.getDataFormatIdentifier() +
									"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
									"| FepSeq:" + fepMessage.getFEPSeq() +
									"| NetworkID: BNK" + 
									"| Transaction Code(service):" + internalFormat.getValue(
											HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
									"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
									"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
									"| Response Code: "+ internalFormat.getValue(
											CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
							 
							 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
							return;
						} // End of insert try-catch
						
                    } catch (IllegalParamException ipe) {
                    	args[0]  = ipe.getMessage();
            			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
            			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
            			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
            			return;
            			
                    } catch (SharedMemoryException sme) {
                    	args[0]  = sme.getMessage();
    					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2020);
    					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2020, args);
    					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(sme));
    					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
    					return;
    					
                    }  catch (ParseException pe) {
                    	args[0] = pe.getMessage();
			            sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_ILLEGAL_PARAM_2100);
			            mcpProcess.writeAppLog(sysMsgID,LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_ILLEGAL_PARAM_2100, args);
			            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(pe));
			            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
						return;
						
					} catch (Throwable th) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
								FepUtilities.getCustomStackTrace(th));
						return;
					} // End of getNetworkKeyForInserting try-catch
					
				}
				// End of Processing for table
				
				/*
				 * Transmit the Request Message.
				 *  Edit the queue header.
				 *   Processing type identifier = "03"
				 *  Data format identifier = "10"
				 */
				newFepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_REQ);
				newFepMessage.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);
				
				/*
				 *  Call sendMessage(String queueName, FepMessage message)
				 *   to send the message to the queue of LCP.
				 *  queueName = mcpProcess.getQueueName(SysCode.LN_BNK_LCP), refer
				 *   to "SS0111 System Code Definition(FEP).xls"
				 */
				newFepMessage.setFEPSeq(fepMessage.getFEPSeq());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : Set FEPSeq : " + fepMessage.getFEPSeq());
				sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), newFepMessage);
				
				try {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <"
							+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
							"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
							"| Data format:" + fepMessage.getDataFormatIdentifier() +
							"| MTI:" + isoMsg.getMTI() +
							"| FepSeq:" + fepMessage.getFEPSeq() +
							"| NetworkID: BNK" + 
							"| ProcessingCode(first 2 digits):" + (
									(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0,2)):"Not Present") + 
							"| Response Code: " + ((isoMsg.hasField(39)) ? isoMsg.getString(39) : "Not Present"));
				} catch (ISOException isoException) {
					args[0]  = isoException.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, 
							CLIENT_SYSLOG_SOCKETCOM_2003);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, 
							CLIENT_SYSLOG_SOCKETCOM_2003, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
							FepUtilities.getCustomStackTrace(isoException));
					return;
				}
				
				/*
				 * Write the Shared Memory.(InternalFormat)
				 * Call getSequence() of IMCPProcess to create the FEP sequence No.
				 * Call super.setTransactionMessage() method.
				 */
				int timeoutValue = Integer.parseInt(String.valueOf(
						mcpProcess.getAppParameter(Keys.XMLTags.AUTHORIZATION_TIMER)));
				setTransactionMessage(fepMessage.getFEPSeq(), timeoutValue, fepMessage);
			}// End of If sign-on and line status is 1(LINE_OK),
		}// End of ELSE		
	}// End of execute
	
	/**
	 * Edit the values of the Fep Message
	 * @param msg The Fep Message to be modified
	 * @return FepMessage The modified Fep Message
	 * @throws HSMException
	 * @throws ISOException
	 * @throws ParseException
	 * @throws SharedMemoryException
	 */
	private FepMessage editMsg(FepMessage msg) throws HSMException, ISOException, ParseException, 
			SharedMemoryException, AeonException {		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : start");
		
		FepMessage newFepMsg = new FepMessage();
		ISOMsg fld48;
		String internalValue;
		String pinAvailable;
		String convertedF7;
		String localDate;
		String localTime;
		String accountNo;
		String field7;
		String pos2;
		String mti;
		String pan;
		String pos;
		String field32;
		String field33;
		int panLength;
		byte[] packedIsoMsg = null;
		Collection<String> keySet;
		Hashtable<String, IInternalKey> fieldList;
		
		// unset fields (common)
		int[] index = {5,6,9,10,15,16,44,50,51,53,62,63,95,102,103,121,123,124,125,127};
		
		/*
		 * In which this line "newFepMsg.setMessageContent(pack(isoMsg));"
		 *  will be equal to "msg.setMessageContent(pack(isoMsg));"
		 *  causing type miscast in Acresponse.
		 *  newFepMsg = msg; <<- Please avoid direct assignment of object at all times
         *  we set msg as newFepMsg so other variables of msg is preserved
         */
		newFepMsg.setFEPSeq(msg.getFEPSeq());
		newFepMsg.setDateTime(msg.getDateTime());
		newFepMsg.setEntryStamp(msg.getEntryStamp());
		newFepMsg.setIPAddress(msg.getIPAddress());
		newFepMsg.setNanoTime(msg.getNanoTime());
		newFepMsg.setPortNumber(msg.getPortNumber());
		newFepMsg.setSAFSendCount(msg.getSAFSendCount() + "");
		newFepMsg.setLogSequence(msg.getLogSequence() + "");
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : newFepMsg");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : FEPSeq : " + newFepMsg.getFEPSeq());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : DateTime : " + newFepMsg.getDateTime());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : EntryStamp : " + newFepMsg.getEntryStamp());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : IPAddress : " + newFepMsg.getIPAddress());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : NanoTime : " + newFepMsg.getNanoTime());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : PortNumber : " + newFepMsg.getPortNumber());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : SAFSendCount : " 	+ newFepMsg.getSAFSendCount());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : Log Sequence : " 	+ newFepMsg.getLogSequence());
		
		
		// Get string values from internal format
		InternalFormat iFormat = (InternalFormat) msg.getMessageContent();
		pinAvailable = iFormat.getValue(CONTROL_INFORMATION.WITH_PIN_OR_NOT).getValue();
		pan = iFormat.getValue(	HOST_COMMON_DATA.PAN).getValue();
		localDate = iFormat.getValue(HOST_COMMON_DATA.LOCAL_TRANSACTION_DATE).getValue();
		localTime = iFormat.getValue(HOST_COMMON_DATA.LOCAL_TRANSACTION_TIME).getValue();

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : pinAvailable : " + pinAvailable);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : pan : " + pan);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : localDate : " + localDate);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : localTime : " + localTime);
		
		InternalFormat internalFormat = msg.getMessageContent();
		
		// Translate from InternalFormat to ISOMsg
		isoMsg = messageConverter.createISOMsgFromInternalFormat(internalFormat);
		isoMsg.setPackager(packager);
		mti = isoMsg.getMTI();
		
		
		if (WITH_TPK_PIN.equals(pinAvailable)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : WITH_TPK_PIN = 2");
			
			// Create the ISOMsg according to the Internal Format.
			HSMCommandCA hsmCommand = new HSMCommandCA();

			// Source TPK
			hsmCommand.setSource_TPK_Name(internalFormat.getValue(CONTROL_INFORMATION.PEK_NAME).getValue());

			// Destination ZPK getsAcquiringEncryptionMode()
			hsmCommand.setDestination_ZPK_Name(super.getAcquiringEncryptionInformationKeyName());

			// Source PIN Block
			hsmCommand.setSource_PIN_block(ISOUtil.hexString(
					internalFormat.getValue(HOST_COMMON_DATA.PIN_VALUE)
						.getValue().getBytes()));

			// [lsosuan:01/03/2011] [Redmine#2116] [[MDS-Acquiring] HSM Error code 24]
			hsmCommand.setSource_PIN_block_format_code(getTpkPinBlockFormatCode());

			// Destination PIN block format code
			// [emaranan:02/12/2011] Add ZPK value from FepMCP.cfg.xml
			hsmCommand.setDestination_PIN_block_format_code(getZpkPinBlockFormatCode());

			pan = internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
			
			// Based on FJ#392 Setting the Account Number
			panLength = pan.length();
			accountNo = pan.substring(panLength - 13, panLength - 1);
			hsmCommand.setAccount_number(accountNo);
				
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : Source_TPK_Name :" 
					+ internalFormat.getValue(CONTROL_INFORMATION.PEK_NAME).getValue());
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : Destination_ZPK_Name :" 
					+ super.getAcquiringEncryptionInformationKeyName());
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : Source_PIN_block :" 
					+ ISOUtil.hexString(internalFormat.getValue(
							HOST_COMMON_DATA.PIN_VALUE).getValue().getBytes()));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : Source_PIN_block_format_code :" 
					+ getTpkPinBlockFormatCode());
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : Destination_PIN_block_format_code :" 
					+ HSM_CMD_DESTINATION_PIN_BLOCK_FORMAT_CODE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : Account_number :" + accountNo);
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : PAN :" + pan);

			HSMThalesAccess hsmThalesAccess = null;
			HSMResponseCB replyHSMMessage = null;
			
			try {
				/*
				 * Create the class that has access the HSM
				 * Send message to HSM
				 */
				hsmThalesAccess = HSMThalesAccess.getInstance();
				replyHSMMessage = (HSMResponseCB) hsmThalesAccess.sendHSMMsg(hsmCommand);
					
			} catch (HSMException e) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e));
				throw e;
			}

			try {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : set ISO field 52 : " 
						+ replyHSMMessage.getDestination_PIN_block());
				isoMsg.set(52, replyHSMMessage.getDestination_PIN_block());
					
			} catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
				return null;
			}
		}// End of processing for pin
		
		isoMsg.unset(index);
				
		/*
		 * [lquirona:208/27/2010] [Redmine#967] [Combine LOCAL TRANSACTION DATE(DE13)
		 *  and LOCAL TRANSACTION Time(DE12) of internal format, and then convert to GMT
		 */
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetAcRequest] : editMsg() : F7 : date : " + localDate + " time = " + localTime);
		field7 =  localDate + localTime;
		
		if(null!=field7 && field7.trim().length() > 0){
			convertedF7 = convTime(field7);
			
			if(null != convertedF7 && convertedF7.trim().length() > 0){
				isoMsg.set(7, convertedF7);
			}
		}
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : F7 : " + isoMsg.getString(7));
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : MTI : " + mti);
		if(SysCode.MTI_0100.equals(mti)){
			//DE 38- unset
			isoMsg.unset(38);
			
			/*
			 * [lsosuan 01/13/2011] [Redmine #2135] [SS0106 SS0109 and SS0107 modification (Issuing)]
			 * DE 55- if IC Flag is "1", IC Request Data from mapIC_Info_Part
			 */
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : IC FLAG :" 
					+ iFormat.getValue(CONTROL_INFORMATION.IC_FLAG).getValue());
			if(Constants.CONTROL_INFORMATION_IC_FLAG_1.equals(
					iFormat.getValue(CONTROL_INFORMATION.IC_FLAG).getValue())) {
				
				iFormat = super.icDataProcess(iFormat);
				isoMsg.set(55, iFormat.getValue(IC_INFORMATION.IC_REQUEST_DATA).getValue().getBytes());
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetAcRequest] : editMsg() : F55 : " + isoMsg.getString(55));
			}

			// Redmine 959 - Set Country Code
			isoMsg.set(20, getAcquiringInstitutionCountryCode());
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : F20 : " + isoMsg.getString(20));
			/*
			 * DE 22 - POS Entry Mode Code
			 * [lquirona:08/27/2010] [Redmine#968] [Field 32 shuold get from configuration file]
			 */
			pos = internalFormat.getValue(OPTIONAL_INFORMATION.POS_ENTRY_MODE).getValue();
			pos2 = internalFormat.getValue(BKNMDSFieldKey.BANKNET_MDS.POS_ENTRY_MODE).getValue();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : Pos (Op Info) = " 
					+ pos + " Pos (BKNFieldKey) = " + pos2);
			
			if(null!= pos) {
				pos.trim();
				if(!NO_SPACE.equals(pos)){
					isoMsg.set(22, pos);
				}
			} else if(null != pos2) {
				pos2.trim();
				if(!NO_SPACE.equals(pos2)){
					isoMsg.set(22, pos2);
					
				}
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetAcRequest] : editMsg() : F22 : " + isoMsg.getString(22));
			
			isoMsg.unset(26);
		
			/*
			 * DE 32- Set value from Configuration File
			 * String f32 = (String)getAcquiringInstitutionIdCode();
			 */
			field32 = getAcquiringInstitutionIdCode();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : f32 : " + field32);
			isoMsg.set(32, field32);
			
			/*
			 * DE 33- Set value from configuration file
			 * String f33 = (String)getForwardingInstitutionIdCode();
			 */
			field33 = getForwardingInstitutionIdCode();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : f33 : " + field33);
			isoMsg.set(33, field33);
			
			//DE 39- unset
			isoMsg.unset(39);
			
			// DE 48- Additional Data
			fld48 = new ISOMsg(48);
			fld48.set(0, "R");
			isoMsg.set(fld48);
			
			// DE 90- unset
			isoMsg.unset(90);
		}
		
		// For fields with BitOFF*1 remarks
		fieldList = new Hashtable<String, IInternalKey>();
		fieldList.put("52", HOST_COMMON_DATA.PIN_VALUE);
		fieldList.put("60", BANKNET_MDS.ADVICE_REASON_CODE);
		fieldList.put("48", OPTIONAL_INFORMATION.ADDITION_DATA);
		fieldList.put("120", BANKNET_ISSFileUpdKey.RECORD_DATA);
		fieldList.put("124", BANKNET_MDS.MEMBER_DEFINED_DATA);

		keySet = (Collection<String>) fieldList.keySet();
		internalValue = "";
		for (String key : keySet) {
			internalValue = iFormat.getValue(fieldList.get(key)).getValue();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : field" + key+ ": " + internalValue);
			
			if(null != internalValue && !SPACE.equals(internalValue) 
					&& (internalValue.trim().length() != 0)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetAcRequest] : editMsg() : field" + key + ": " + internalValue);
				isoMsg.set(key,internalValue);
			}
		}
		
		packedIsoMsg = pack(isoMsg);
        
        // If error occurs,
        if(packedIsoMsg.length < 1) {
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest]Error in Packing Message");
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] editMsg(): End Process");
                    return null;
        } // End of if bytesMessage is 0 
        
		// WriteApplog the newFepMsg
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() : isoMsg : " + isoMsg);
				
		if (null == packedIsoMsg || 0 == packedIsoMsg.length) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "Error packing the ISO msg.");
			iFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_BNK_RESPONSE_CODE_APPROVED);
			newFepMsg.setMessageContent(internalFormat);
			newFepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
			newFepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

            mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] Message sent to queue: " 
            		+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) 
            		+ " | Process type: " + newFepMsg.getProcessingTypeIdentifier() 
            		+ " | Data format: " + newFepMsg.getDataFormatIdentifier() 
            		+ " | Message: " + newFepMsg.getMessageContent() 
            		+ " | Timestamp: " + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()));
            
            return null;
		}
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : " + ISOUtil.hexString(packedIsoMsg));
		
		newFepMsg.setMessageContent(packedIsoMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : editMsg() end");
		return newFepMsg;
	}// End of editMsg
	
	
	/**
	 * Create the key for searching original message.
	 * 2010-09-22 lquirona
	 * @param msg The Fep Message received which contains Internal Format
	 * @return String The Network Key created used for inserting a record
	 */
	private String getNetworkKeySearchingOriginal(FepMessage msg){
		String key = NO_SPACE;
		String pan;
		String procCode;
		String ecrRefNo;
		String retRefNum;
		String msgTypeId;
		
		// Get the internal format
		InternalFormat internalFormat = (InternalFormat)msg.getMessageContent();
		
		// Get the Source ID
		String sourceId = internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue();
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() start");
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() : sourceId : " + sourceId);
		// If Source ID of the Internal Format is EDC
		if(SysCode.NETWORK_EDC.equals(sourceId)){
			
			// Get the values needed for EDC
			pan = internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
			procCode = internalFormat.getValue(HOST_COMMON_DATA.PROCESSING_CODE).getValue();
			ecrRefNo = internalFormat.getValue(EDC_SHOPPING.INVOICE_ECR_REF_NO).getValue();
			msgTypeId = internalFormat.getValue(EDC_SHOPPING.MESSAGE_TYPE_ID).getValue();
			
			// Network Key Searching Original = HOST_COMMON_DATA.PAN + EDC_SHOPPING.INVOICE_ECR_REF_NO
			key += pan;
			key += ecrRefNo;
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() : pan : " + pan);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() : procCode : " + procCode);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() : ecrRefNo : " + ecrRefNo);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() : msgTypeId : " + msgTypeId);
			
			// If EDC_SHOPPING.MESSAGE_TYPE_ID is "0200", + EDC_SHOPPING.MESSAGE_TYPE_ID 
			if(SysCode.MTI_0200.equals(msgTypeId)){
				key += msgTypeId;
					
				// If first two char of HOST_COMMON_DATA.PROCESSING_CODE is "02", + "00"
				if(procCode.startsWith("02")){
					key += "00";
				}
					
				// If first two char of HOST_COMMON_DATA.PROCESSING_CODE is "22", + "20"
				else if(procCode.startsWith("22")){
					key += "20";
				}
			}// End of if
				
			// If EDC_SHOPPING.MESSAGE_TYPE_ID is "0400", + "0200" + first 2 char of HOST_COMMON_DATA.PROCESSING_CODE
			else if(SysCode.MTI_0400.equals(msgTypeId)){
				key += "0200";
				key += procCode.substring(0,2);
			}
		}// End of processing for EDC
		
		// If Source ID of Internal Format is "PGW"
		else if(SysCode.NETWORK_PGW.equals(sourceId)){
			pan = internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
			retRefNum = internalFormat.getValue(HOST_COMMON_DATA.RETRIEVAL_REF_NUMBER).getValue();
			
			key += pan; 
			key += retRefNum;
		    
			// Return Net Work Key Searching Original 
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() : pan : " + pan);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() : retRefNum : " + retRefNum);
		}
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginal() end");
		return key;
	}// End of getNetworkKeySearchingOriginal
	
	/**
	 * Create the key for inserting
	 * 2010-09-22 lquirona
	 * @param msg The Fep Message received which contains Internal Format
	 * @return String The Network Key created used for inserting a record
	 * @throws Exception 
	 */
	private String getNetworkKeyForInserting(FepMessage msg) throws Exception{
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeyForInserting() start");
        
        /*
         * Network Key = MTI + HOST_COMMON_DATA.PAN + HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER(STAN) + 
         * "YYYY+HOST_COMMON_DATA.LOCAL_TRANSACTION_DATE(MMDD)"
         * 
         * Get the internal format
         */
		InternalFormat internalFormatParameter = (InternalFormat)msg.getMessageContent();
		String msgTypeId = NO_SPACE;
        String pan = NO_SPACE;
        String stan = NO_SPACE;
		String transCode = NO_SPACE;
        String key = NO_SPACE;
        String gmtF7 = NO_SPACE;
        String year;
		String date;
		String time;
		String field7;
        
		// [lsosuan:11/24/2010] [Redmine#1926] [acquiring the mti base on the transaction code]
		transCode = internalFormat.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeyForInserting() : transCode : " + transCode);

        // [emaranan:02/24/2011] [Redmine#2198] [AMX-ACQ] EDC Void Sale
    	// MTI
        if (transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE)) {
    		msgTypeId = SysCode.MTI_0400;
    	} else if (transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_REQ_1)) {
    		msgTypeId = SysCode.MTI_0400;
    	} else if (transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REQ_4)) {
    		msgTypeId = SysCode.MTI_0100;
    	} else if (transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REQ_1)) {
    		msgTypeId = SysCode.MTI_0100;
    	}
    	
        // STAN [lquirona - 03/29/2011 - Redmine Issue #2210 - set new stan for 0400]
        if((SysCode.MTI_0400).equals(msgTypeId)) {
            stan = stanForInserting;
        }else{
            stan = internalFormatParameter.getValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER).getValue();
        }
        
		// PAN
		pan = internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
			
		// String YYYY from system time
        date = internalFormat.getValue(HOST_COMMON_DATA.LOCAL_TRANSACTION_DATE).getValue();
        time = internalFormat.getValue(HOST_COMMON_DATA.LOCAL_TRANSACTION_TIME).getValue();
		year = String.valueOf(FepUtilities.getYear(mcpProcess,date));

        field7 =  date + time ;
		if(null!=field7 && field7.trim().length() > 0){
			gmtF7 = convTime(field7);
		}
		key = msgTypeId + pan + stan + year + gmtF7;

        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeyForInserting() end");
		//return key
		return key;
	}// end of getNetworkKey
	
	/**
	 * Create the key for inserting
	 * @param msg The Fep Message received which contains Internal Format
	 * @return String The Network Key created used for inserting a record for reversal transactions
	 */
	private String getNetworkKeySearchingOriginalForInserting(FepMessage msg){
        String key = NO_SPACE;
        String stan;
        
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginalForInserting() start");
        
        //Get the internal format
        InternalFormat internalFormat = (InternalFormat)msg.getMessageContent();
        
        //Get the Source ID
        String sourceId = internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue();
        
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : getNetworkKeySearchingOriginalForInserting() :" +
        		" sourceId : " + sourceId);
        
        //If Source ID of Internal Format is "EDC"
        if(SysCode.NETWORK_EDC.equals(sourceId)){
        	
            /*
             * Net Work Key Searching Original = HOST_COMMON_DATA.PAN 
             *      + EDC_SHOPPING.INVOICE_ECR_REF_NO 
             *      + EDC_SHOPPING.MESSAGE_TYPE_ID 
             *      + first two char of HOST_COMMON_DATA.PROCESSING_CODE
             */
            key += internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
            key += internalFormat.getValue(EDC_SHOPPING.INVOICE_ECR_REF_NO).getValue();
            key += internalFormat.getValue(EDC_SHOPPING.MESSAGE_TYPE_ID).getValue();
            key += (internalFormat.getValue(HOST_COMMON_DATA.PROCESSING_CODE).getValue()).substring(0, 2);
        }// End of EDC processing
        
        // If Source ID of Internal Format is "PGW"
        else if(SysCode.NETWORK_PGW.equals(sourceId)){
            // PAN
            key += internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
            
            // [emaranan:03/07/2011] [Redmine#2233] Change from substring(0,4) to substring(4,8)
            // Transaction Date MMDD
            key += internalFormat.getValue(
            		HOST_COMMON_DATA.TRANSACTION_DATE).getValue().substring(4,8);
            
            // Transaction Time hhmmss
            key += internalFormat.getValue(HOST_COMMON_DATA.TRANSACTION_TIME).getValue();
            
            // Last 2 char of STAN
            stan = internalFormat.getValue(
            		HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER).getValue();
            key += stan.substring(4,6);
            
            /*
             * Net Work Key Searching Original = HOST_COMMON_DATA.PAN + HOST_COMMON_DATA.TRANSACTION_DATE(MMDD) 
             * + HOST_COMMON_DATA.TRANSACTION_TIME(hhmmss) + Last two char of HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER
             */
        
        }// End of PGW processing
        
        // Return Net Work Key Searching Original 
        return key;
        
	}// End of getNetworkKeySearchingOriginalForInserting
	
	/**
	  * Method to set original transaction amounts used in execute method.
	  * @param originalRequestIsoMsg The original request ISOMsg from the database
	  * @param originalResponseIsoMsg The original response ISOMsg from the database
	  * @return reversalEditedMessage The message to be updated
	  * @throws Exception 
	  * */
	private byte[] setOriginalTransactionValues(ISOMsg originalRequestIsoMsg, ISOMsg originalResponseIsoMsg, 
			byte[] reversalEditedMessage) throws Exception{
		
		String origBit7 = NO_SPACE;
		String bit90 = NO_SPACE;
		String origBit13;
		String origBit12;
		String field7;
		String convertedF7;
		String mti;
		String origBit11;
		String origBit32;
		String origBit33;
		String bit63Sub1;
		String bit63Sub2;
		String bit15;
		String field48Sub63;
		String field37;
		final int FLD32_LENGTH = 11;
		final int FLD33_LENGTH = 11;
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() start");
		
		/*
		 * Get the byte message (reversal) to be updated
		 * Unpack the ISOMsg
		 */
		byte[] message = reversalEditedMessage;
		ISOMsg editedISOmsg = new ISOMsg();
		
		ISOMsg field48;
		String field63;
		
		try {
			editedISOmsg.setPackager(new GenericValidatingPackager(getPackageConfigurationFile()));
		
		} catch (ISOException ie) {
			args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
			return null;
		}
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : Unpacking ISOMsg");
		editedISOmsg.unpack(message);
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : Reversal ISO" + editedISOmsg);
		
		// Redmine Issue #2167
		// [emaranan:02/21/2011] [Redmine#2210] [ACQ] MTI with 0400 Processing
		int[] fldList = {2,3,4,12,13,14,18,22,23,32,33,42,43,49};

		for (int i : fldList) {
			if(originalRequestIsoMsg.hasField(i)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
						"Copied Value = " + i + ",  " + originalRequestIsoMsg.getString(i));
				editedISOmsg.set(i,originalRequestIsoMsg.getString(i));
			}
		}
		if(originalRequestIsoMsg.hasField(61))
			editedISOmsg.set((ISOMsg)originalRequestIsoMsg.getValue(61));
		
		field37 = originalRequestIsoMsg.getString(37);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
				"Original Request field 37 :" + field37);
		
		if(originalRequestIsoMsg.hasField(37)) {
			editedISOmsg.set(37, field37);
		}
				
		// Set Bit 48 for 0400
		field48 = (ISOMsg)originalRequestIsoMsg.getValue(48);
		field63 = originalResponseIsoMsg.getString(63);
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
				"Original Response field 63 :" + field63);
		
		if(originalRequestIsoMsg.hasField(48)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, " [BanknetAcRequest] : setOriginalTransactionValues() :" +
					"Original Response has field 48 :" + field48);
			
			/*
			 * field 48.20 If Bit52 of the original transaction is ON,
			 *   set "2001P"  
			 * for other cases,
			 *  set "2001S".
			 */
			if(originalRequestIsoMsg.hasField(52))
				field48.set(20,"P");
			else
				field48.set(20,"S");
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
					"field 48.20 :" + field48.getString(20));
			
			if(originalResponseIsoMsg.hasField(63)) {
				
				bit63Sub1 = field63.substring(0, 3);
				bit63Sub2 = field63.substring(3, 9);
				bit15 = originalResponseIsoMsg.getString(15);
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
						"field 63.1 :" + bit63Sub1);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
						"field 63.2 :" + bit63Sub2);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
						"field 15 :" + bit15);
				
				// field 48.63 set "6315"+Bit63.1 of  the original transaction+Bit63.2+Bit15+two spaces
				field48Sub63 = bit63Sub1 + bit63Sub2 + bit15 + SPACE_00;
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
						"field 48.63 :" + field48Sub63);
				field48.set(63,field48Sub63);
			}
			
			editedISOmsg.set(field48);
		}
		
		origBit7 = NO_SPACE;
		bit90 = NO_SPACE;
		
		// Combine LOCAL TRANSACTION DATE(DE13) and LOCAL TRANSACTION Time(DE12) of internal format, and then convert to GMT
		origBit13 = editedISOmsg.getString(13);
		origBit12 = editedISOmsg.getString(12);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
				"(reversal) F7: date = " + origBit13 + " time = " + origBit12);
		field7 =  origBit13 + origBit12 ;
		if(null!=field7 && field7.trim().length() > 0){
			convertedF7 = convTime(field7);
			if(null != convertedF7 && convertedF7.trim().length() > 0){
				origBit7 = convertedF7;
			}
		}

		/*
		 *  [emaranan:02/28/2011] Redmine 2167 0400 must have a unique DE 7 value assigned. 
		 *  [salvaro:10/08/2011] Edited values for getting the Original STAN
		 */
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "field 7 :" + originalRequestIsoMsg.getString(7));
				
		mti = SysCode.MTI_0100;
		origBit11 = originalRequestIsoMsg.getString(11);
		origBit32 = ISOUtil.padleft(originalRequestIsoMsg.getString(32), FLD32_LENGTH, Constants.cPAD_0);
		origBit33 = ISOUtil.padleft(originalRequestIsoMsg.getString(33), FLD33_LENGTH, Constants.cPAD_0);
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
				"field 11 :" + origBit11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
				"field 32 :" + origBit32);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
				"field 33 :" + origBit33);
						
		bit90 = mti + origBit11 + origBit7 + origBit32 + origBit33;
		
		// Set Bit 90 in reversal message
		editedISOmsg.set(90, bit90);
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
				"field 90 :" + editedISOmsg.getString(90));
		
		// Set Bit 38
		if(originalResponseIsoMsg.hasField(38)) {
			editedISOmsg.set(38, originalResponseIsoMsg.getString(38));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
					"field 38 :" + editedISOmsg.getString(38));
		}
		
		// Set Bit 39 from original response
		if(originalResponseIsoMsg.hasField(39)) {
			editedISOmsg.set(39, originalResponseIsoMsg.getString(39));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() : " +
					"field 39 :" + editedISOmsg.getString(39));
		}
		
		// [lquirona - 03/29/2011 - Redmine Issue #2210 - Set bit 11 to be used in inserting network key]
		stanForInserting = editedISOmsg.getString(11);
		
		editedISOmsg.recalcBitMap();
		FepUtilities.writeISOMsgtoAppLog(mcpProcess, editedISOmsg);
		
		message = pack(editedISOmsg);
        
        // If error occurs,
        if(message.length < 1) {
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest]Error in Packing Message");
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] setOriginalTransactionValues(): End Process");
                    return null;
        } // End of if bytesMessage is 0 
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : setOriginalTransactionValues() end");
		return message;
	}// End of setOroginaltransactionValues
	
	/**
	 * Converts the System Time to GMT Format
	 * @param time The String value
	 * @return String The converted time
	 * @throws ParseException
	 * @throws SharedMemoryExceptions
	 * */
	 private String convTime(String time) throws ParseException, SharedMemoryException {
		 String gmtStr;

		 SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_MMDDhhmmss);
		 long millis = sdf.parse(time).getTime();
		 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : convTime() : time: " + time);
		 long gmt = millis - Calendar.getInstance().getTimeZone().getRawOffset();
		 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : convTime() : offset: " + mcpProcess.getOffsetTime());
		 Calendar cal = Calendar.getInstance();
		 cal.setTimeInMillis(gmt);
		 gmtStr = sdf.format(cal.getTime());
		 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : convTime() : gmtStr: " + gmtStr);
		 return gmtStr;
	}// End of convTime
	 
    /**
     * This method will check if Merchant ID and Terminal ID exists in EDC Master Table
     * @param fepMessage The Fep Message received from MsgDispatcher
     * @return boolean True if found otherwise false
     */ 
    private Boolean checkMerchantIDANDTerminalID(FepMessage fepMessage) {
        // [emaranan:03/02/2011] [Redmine#2230] [EDC] SS document update
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcRequest] : checkMerchantIDANDTerminalID() start");
        Boolean returnVal = false;
        try {
            List<FEPT084EdcmsttblDomain> resultList = null;
            InternalFormat internalFormatParameter = fepMessage.getMessageContent();
            checkDomain = new FEPT084EdcmsttblDomain();
            checkDomain.setTerminal_id(
                    internalFormatParameter.getValue(HOST_COMMON_DATA.CARD_ACCEPTOR_TERMINAL_ID).getValue());
            checkDomain.setMerchant_id(
                    internalFormatParameter.getValue(HOST_COMMON_DATA.CARD_ACCEPTOR_IDENTIFICATION_CODE).getValue());
            resultList = checkDAO.findByCondition(checkDomain);
            if(resultList != null || !resultList.isEmpty()) {
                returnVal = true;
            }
        } catch (Exception e) {
        	args[0]  = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] execute(): End Process");
			return null;
        }
        return returnVal;
    }
	 
}// End of class
