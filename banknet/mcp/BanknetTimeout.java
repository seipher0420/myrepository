/*
 * Copyright (c) 2008-2010 AEON Credit Technology Systems, Inc. All Rights Reserved.VALIDATE_NO_ERROR
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
 * DATE          AUTHOR          REMARKS / COMMENT
 * ====----------======----------=================------------------------------
 * June-01-2010  ccalanog        V0.0.1   : updated static variables from SysCode
 * May-20-2010   fsamson         V0.0.1   : updated judge() method
 * May-12-2010   fsamson         V0.0.1   :(1 )removed FepMessage instance var
 *                                         (2 )removed overloaded Constructor
 *                                         (3 )resolved issues
 * Apr-22-2010   fsamson         V0.0.1   : Initial code
 * 
 */
package com.aeoncredit.fep.core.adapter.brand.banknet.mcp;

import com.aeoncredit.fep.common.SysCode;

import static com.aeoncredit.fep.common.SysCode.LN_ACE_STORE;
import static com.aeoncredit.fep.common.SysCode.LN_HOST_STORE;
import static com.aeoncredit.fep.common.SysCode.LN_MON_STORE;
import static com.aeoncredit.fep.common.SysCode.MTI_0190;
import static com.aeoncredit.fep.common.SysCode.MTI_0110;
import static com.aeoncredit.fep.common.SysCode.QH_DFI_FEP_MESSAGE;
import static com.aeoncredit.fep.common.SysCode.QH_PTI_SAF_REQ;
import static com.aeoncredit.fep.common.SysCode.QH_PTI_MCP_RES;
import static com.aeoncredit.fep.common.SysCode.QH_DFI_ORG_MESSAGE;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Level;
import org.jpos.iso.ISODate;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericValidatingPackager;

import com.aeoncredit.fep.core.adapter.brand.common.Constants;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.internalmessage.FepMessage;
import com.aeoncredit.fep.core.internalmessage.InternalFormat;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.CONTROL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_COMMON_DATA;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_FEP_ADDITIONAL_INFO;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_HEADER;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.OPTIONAL_INFORMATION;
import com.aeoncredit.fep.framework.dbaccess.FEPT022BnkisDAO;
import com.aeoncredit.fep.framework.dbaccess.FEPT022BnkisDomain;
import com.aeoncredit.fep.framework.dbaccess.FEPTransactionMgr;
import com.aeoncredit.fep.framework.exception.AeonCommonException;
import com.aeoncredit.fep.framework.exception.AeonDBException;
import com.aeoncredit.fep.framework.exception.AeonException;
import com.aeoncredit.fep.framework.exception.DBNOTInitializedException;
import com.aeoncredit.fep.framework.exception.IllegalParamException;
import com.aeoncredit.fep.framework.exception.SharedMemoryException;
import com.aeoncredit.fep.framework.log.LogOutputUtility;
import com.aeoncredit.fep.framework.mcp.IMCPProcess;
/**
 * Get the message from the shared memory and judge it according to its MTI
 * 
 * @author FSamson
 * @version 0.0.1
 * @see com.aeoncredit.fep.core.adapter.brand.common.BanknetCommon
 * @since 0.0.1
 */
public class BanknetTimeout extends BanknetCommon {

	/** Process of creating Internal Format**/
	private BanknetInternalFormatProcess inFormatProcess;

	/** contains the internal format reply from FEP */
	private FepMessage parameterMessage;

	/** dao */
	private FEPT022BnkisDAO dao;

	/** transaction manager */
	private FEPTransactionMgr transactionManager;

	/** table domain */
	private FEPT022BnkisDomain domain;

	/** variables for exception handling */
	private String[] args = new String[2];
	private String sysMsgID  = NO_SPACE;
	

	private int zeroResendCount = 0;

	/**
	 * Constructor, called when this class is created
	 * @param mcpProcess The instance of AeonProcessImpl
	 */
	public BanknetTimeout(IMCPProcess mcpProcess) {
		super(mcpProcess);

		try {
			transactionManager = mcpProcess.getDBTransactionMgr();
			dao = new FEPT022BnkisDAO(transactionManager);
			domain = new FEPT022BnkisDomain();
		} catch (DBNOTInitializedException dnie) {
			args[0]  = dnie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(dnie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout]: End Process");
			return;
		} // end of try - catch
	} // end of constructor

	/**
	 * Retrieves the message from the shared memory
	 * then validate the message according to its MTI, the method to be
	 * called upon initialization of this object, it is also used to inject 
	 * the instance of FepMessage that will be used in the transaction.
	 * @param fepMessage The Fep Message received
	 */
	public void execute(FepMessage fepMsg) {   	
		String msgSeqNum;
		Throwable cause;
		AeonCommonException aeonException;
		String sysMsgId;

		try {
			msgSeqNum = fepMsg.getFEPSeq();          

			/* 
			 * Get the message FEP sequence No.
			 * Using the FEP sequence No, get the message from the shared memory.
			 * 		Call super.getTransactionMessage() method.
			 * Call judge() method
			 */
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"********** BRAND MCP TIMEOUT START PROCESS **********");
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetTimeout] Method Start: execute()");

			fepMsg = getTransactionMessage(msgSeqNum);

			if(null != fepMsg){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetTimeout] execute(): FepMessage: " + fepMsg);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetTimeout] execute(): Check Message Processing Type");
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetTimeout] execute(): Call judge() Method)");
				this.judge(fepMsg);
			} else {
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, 
						CLIENT_SYSLOG_INFO_2129);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, 
						CLIENT_SYSLOG_INFO_2129, null); 
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetTimeout] : sharedFepMessage is null. Return from execute()");
			}// End of if-else

		} catch(Exception e) {
			cause = e.getCause();

			if (cause !=null && (cause instanceof AeonCommonException)) {
				aeonException = (AeonCommonException) cause;
				sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, 
						aeonException.getMsgID(), aeonException.getMsgParam());
				mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, 
						aeonException.getMsgID(), aeonException.getMsgParam(), 
						aeonException.getInputParam(), aeonException);
			} else {
				mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, e.getMessage());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, e.getMessage());            
			} // End of cause value checking
			
			args[0]  = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, 
					CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, 
					CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
					FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetTimeout] execute(): End Process");
			return;
		} // End of try catch

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"********** BRAND MCP TIMEOUT END PROCESS **********");
	} // End of execute()

	/**
	 * This method validates the message according to its MTI on which action
	 * should be called when a timeout occurs
	 * @see #execute()
	 * @param msg The received Fep Message
	 * @throws ISOException
	 * @throws NullPointerException
	 */
	private void judge(FepMessage fepMsg) throws Exception {   	
		String dataFormatId;
		InternalFormat internalMsg;
		String mti;

		/*
		 * If Data format identifier = "01"(Internal Format),
		 * 		If MTI = "0100",
		 * 			Call timeout_0100() method.
		 * 		If MTI = "0302",
		 * 			Call timeout_0302() method.
		 * 		If MTI = "0800",
		 * 			Call timeout_0800() method.
		 */
		dataFormatId = fepMsg.getDataFormatIdentifier();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout] judge() start");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout] judge(): DFI: " + dataFormatId);

		if (SysCode.QH_DFI_FEP_MESSAGE.equals(dataFormatId)) {
			internalMsg = fepMsg.getMessageContent();
			mti = internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetTimeout] judge(): MTI: " + mti);

			if (SysCode.MTI_0100.equals(mti)) {  
				this.timeout_0100(fepMsg);

			} else if (SysCode.MTI_0302.equals(mti)) {  
				this.timeout_0302(fepMsg);

			} else if (SysCode.MTI_0800.equals(mti)) {  
				this.timeout_0800(fepMsg);
			} // End of MTI value checking

			/*
			 * If Data format identifier = "10"(ISOMsg),
			 * 		If resendCount(from the queue header)=0,
			 * 			call timeout_Issuing() method.
			 * 		else
			 * 			call timeout_0400() method.
			 */

		} else if (SysCode.QH_DFI_ORG_MESSAGE.equals(dataFormatId)) {

			if (fepMsg.getSAFSendCount() == zeroResendCount) {
				this.timeout_Issuing(fepMsg);

			} else {
				this.timeout_0400(fepMsg);
			} // End of resendCount value checking

		} // End of dataFormatId value checking
	} // End of judge()

	/**
	 * Timeout method for handling 0100, called by the judge method
	 * @see    #judge(FepMessage)
	 * @param  msg The received Fep Message which is from shared memory
	 * @throws ISOException
	 * @throws AeonException 
	 */
	private void timeout_0100(FepMessage fepMsg) throws Exception {  	
		/*
		 * Edit the decline response message(Internal Format)
		 * Transmit the response message
		 * 		Edit the queue header
		 * 			Processing type identifier = "22"
		 * 			Data format identifier = "01"
		 * 		Call sendMessage(String queueName, FepMessage message) to send the
		 * 		message to the queue of FEP processing function
		 * 		queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP),
		 * 		refer to "SS0111 System Code Definition(FEP).xls"
		 * 
		 */
		InternalFormat internalMsg = fepMsg.getMessageContent();
		internalMsg.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_RESPONSE_CODE_TRANSACTION_TIMED_OUT);

		fepMsg.setMessageContent(internalMsg);

		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMsg);

		// [MQuines: 06-24-2011] Logging Purposes
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"receiving a message from <TIMEOUT>");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"sending a message to <"+ 
				mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalMsg.getValue(
						OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| fepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalMsg.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID:" + internalMsg.getValue(
						HOST_HEADER.SOURCE_ID).getValue() +
				"| DestinationId:" + internalMsg.getValue(
						HOST_HEADER.DESTINATION_ID).getValue() + 
				"| FEP response Code:" + internalMsg.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

	} // End of timeout_0100()

	/**
	 * Timeout method for handling 0400, called by the judge method
	 * @see    #judge(FepMessage)
	 * @param  msg The received Fep Message which is from shared memory
	 * @throws ISOException
	 * @throws AeonException 
	 */
	private void timeout_0400(FepMessage fepMsg) throws Exception {
		/*
		 * Edit the queue header.
		 * 		Processing type identifier = "07"
		 * 		Data format identifier = "10"
		 * Call sendMessage(String queueName, FepMessage message) to send the
		 * message to the queue of FORWARD process
		 * queueName = mcpProcess.getQueueName(SysCode.LN_BNK_FORWARD)
		 * refer to "SS0111 System Code Definition(FEP).xls"
		 */
		try {
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REP);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

			sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_FORWARD), fepMsg);

			// [MQuines: 06-23-2011] Added for Logging Purposes
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"receiving a message from <TIMEOUT>");
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"sending a message to <"+ 
					mcpProcess.getQueueName(SysCode.LN_BNK_FORWARD) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| fepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK");  

		} catch(Exception e) {
			args[0]  = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout] execute(): End Process");
			return;
		}
	} // End of timeout_0400()

	/**
	 * Timeout method for handling 0302, called by the judge method
	 * @see    #judge(FepMessage)
	 * @param  msg The received Fep Message which is from shared memory
	 * @throws ISOException
	 * @throws AeonException 
	 */
	private void timeout_0302(FepMessage fepMsg) throws Exception {   	
		InternalFormat internalMsg;

		/*
		 * Edit the decline response message(Internal Format)
		 * Transmit the response message
		 * 		Edit the queue header
		 * 			Processing type identifier = "22"
		 * 			Data format identifier = "01"
		 * 		Call sendMessage(String queueName, FepMessage message) to send the
		 * 		message to the queue of FEP processing function
		 * 		queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP)
		 * 		refer to "SS0111 System Code Definition(FEP).xls"
		 * 
		 */
		internalMsg = fepMsg.getMessageContent();
		internalMsg.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, 
				FEP_RESPONSE_CODE_TRANSACTION_TIMED_OUT);

		fepMsg.setMessageContent(internalMsg);
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMsg);

		// [MQuines: 06-24-2011] Logging Purposes
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"receiving a message from <TIMEOUT>");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"sending a message to <"+ 
				mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalMsg.getValue(
						OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| fepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalMsg.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID:" + internalMsg.getValue(
						HOST_HEADER.SOURCE_ID).getValue() +
				"| DestinationId:" + internalMsg.getValue(
						HOST_HEADER.DESTINATION_ID).getValue() + 
				"| FEP response Code:" + internalMsg.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

	} // End of timeout_0302()

	/**
	 * Timeout method for handling 0800, called by the judge method
	 * @see    #judge(FepMessage)
	 * @param  msg The received Fep Message which is from shared memory
	 * @throws ISOException
	 * @throws AeonException 
	 */
	private void timeout_0800(FepMessage fepMsg) throws Exception {

		String sysMsgID;
		/*
		 * Output the System Log 		IMCPProcess.writeSysLog(Level.INFO, "CSW2132")
		 * Output the transaction history log 
		 * 		Edit the queue header
		 * 			Processing type identifier = "05"
		 * 			Data format identifier = "01"
		 * 		Call sendMessage(String queueName, FepMessage message) to send the
		 * 		message to the queue of Monitor STORE process
		 * 		queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),
		 * 		refer to "SS0111 System Code Definition(FEP).xls"
		 */
		mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_DEBUG, CLIENT_SYSLOG_SCREEN_2132);
		sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_DEBUG, CLIENT_SYSLOG_SCREEN_2132);
		mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2132, null);

		InternalFormat inFormatMsg = fepMsg.getMessageContent();
		String transCode = inFormatMsg.getValue(
				HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue();
        
        // [MQuines: 10/03/2011] Set Source ID This is used by Monitoring Screen for display in transaction history log
		inFormatMsg.setValue(HOST_HEADER.SOURCE_ID, OUTRESCNV_NW_CODE_BANKNET);
		
		// Check the transaction code and set Error Message in PAN
		if(null!= transCode && transCode.length() > 0){
			
			if(transCode.equals(TRANS_CODE_000001)){
				// Sign On
				inFormatMsg.setValue(HOST_COMMON_DATA.PAN, MSG_ERROR_SIGNONON_BNK);
			}else if(transCode.equals(TRANS_CODE_000002)){
				// Sign Off
				inFormatMsg.setValue(HOST_COMMON_DATA.PAN, MSG_ERROR_SIGNONOFF_BNK);
			}else if(transCode.equals(TRANS_CODE_000005)){
				// Key Exchange Error
				inFormatMsg.setValue(HOST_COMMON_DATA.PAN, MSG_ERROR_KEYEXCHANGE_BNK);
			}else if(transCode.equals(TRANS_CODE_000009)){
				// Echo Test
				inFormatMsg.setValue(HOST_COMMON_DATA.PAN, MSG_ERROR_ECHOTEST_BNK);
			}			
			/* [mqueja: 11/22/2011] [removed TRANS_CODE_000007 based on SS Document update]
			 * [MQuines: 11/21/2011] Removed TRANS_CODE_000008 since this method is used for request messages only
			 */
			inFormatMsg.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			
		}// End of check if transaction code has value
		
		// [MQuines: 10/03/2011] Update the message content of fepmsg
		fepMsg.setMessageContent(inFormatMsg);
		
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);  

		InternalFormat internalFormat = fepMsg.getMessageContent();

		// [MQuines: 06-24-2011] Logging Purposes
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"receiving a message from <TIMEOUT>");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" + 
				mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalFormat.getValue(
						OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormat.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalFormat.getValue(
						HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalFormat.getValue(
						HOST_HEADER.DESTINATION_ID).getValue() +
				"| Response Code: "+ internalFormat.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

	} // End of timeout_0800()

	/**
	 * Timeout method for Issuing Message, called by the judge method
	 * @see    #judge(FepMessage)
	 * @param  msg The received Fep Message which is from shared memory
	 * @throws ISOException
	 * @throws AeonException 
	 */
	private void timeout_Issuing(FepMessage fepMsg) throws Exception {    	
		String sysMsgId;
		FepMessage parameterMessageClone = new FepMessage();
		String origProcDateTime = NO_SPACE;
		String fepResponseCode = NO_SPACE;
		String extResponseCode = NO_SPACE;
		boolean sendTimeoutResponse = false;
		String originalFepSeq = NO_SPACE;
		String originalTransCode = NO_SPACE;
		String originalProcessDateTime = NO_SPACE;
		String msgProcessingNum = NO_SPACE;
		
		// Output the System Log.

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "receiving a message from <TIMEOUT>");
		mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_INFORMATION, EXCEPTION_TIMOUT_MESSAGE);

		/*
		 * [mqueja: 06/16/2011] [Redmine #2194]
		 * [Create reversal advice when FEP is down and the transaction is approved by HOST] 
		 * 
		 * Generates a reversal message to cancel the message, and sends a reversal message to HOST via HOSTSAF,
		 * and sends a notification message to ACESAF and MONSAF. 
		 *    Generates a reversal message
		 *    Update the BANKNET Issuing Log Table.
		 *    Edit the queue header.
		 *    Call sendMessage(String queueName, FepMessage message) to send the message to the queue of 
		 *       HOST STORE process.
		 *    Call sendMessage(String queueName, FepMessage message) to send the message to the queue of 
		 *       Monitor STORE process.
		 *    Call sendMessage(String queueName, FepMessage message) to send the message to the queue of 
		 *       ACE STORE process.
		 */
		parameterMessage = fepMsg;

		if (null == parameterMessage) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
					"[BanknetTimeout] timeout_Issuing(): SharedMemory Message returned null");
			return;
		}

		ISOMsg reversalIsoMsg = null;
		String queueName = null;

		String origLocalDate = parameterMessage.getDateTime().substring(2, 8);
		String origLocalTime = parameterMessage.getDateTime().substring(8, 14);
		origProcDateTime = origLocalDate + origLocalTime;

		// Insert to table 0100 (internal format)
		try {
			insertToLogTable(COL_PROCESS_STATUS_APPROVED, COL_PROCESS_RESULT_APPROVE);

		} catch (Exception e) {
			args[0]  = e.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetTimeout] execute(): End Process");
			return;
		}
		
		// Get SEND_TIMEOUT_RESPONSE_FLAG value from config
		sendTimeoutResponse = SEND_TIMEOUT_RESPONSE_ON.equals(getSendTimeoutResponseFlag());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout] Send Timeout Response Flag: " + sendTimeoutResponse);
		
		//	[mqueja:05/23/2012] [Redmine#3873] [Sending a decline Response to Banknet on Timeout]
		parameterMessageClone = copyFepMessageHeader(fepMsg);		
		ISOMsg respIsoMsg = (ISOMsg) parameterMessageClone.getMessageContent();
		
		inFormatProcess = new BanknetInternalFormatProcess(mcpProcess);
		InternalFormat reqInternalFormat = inFormatProcess.createInternalFormatFromISOMsg(respIsoMsg);
		currencyCodeCheck(reqInternalFormat, respIsoMsg);
		
		if(null == reqInternalFormat || SPACE.equals(reqInternalFormat)){
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
			"[BanknetTimeout]: timeout_Issuing(): requestInternalFormat is null");
		}
		try {
			String timeStamp = mcpProcess.getSystime();
			final String processIOType = PROCESS_I_O_TYPE_ISRES;
			final String processNo = mcpProcess.getProcessName();

			reqInternalFormat.addTimestamp(processNo, timeStamp, processIOType);

		} catch (SharedMemoryException sme) {
			args[0]  = sme.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout] execute(): End Process");
			return;
		}
		
 		// set response code and sequence number
		if (respIsoMsg.getMTI().startsWith(PREFIX_MTI_01)) {
			//[rrabaya:11/27/2014]RM#5850 Set correct External Response Code for ASI transaction : Start
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "Transaction Code: "
					+ FepUtilities.isASITransaction(reqInternalFormat));
			if(FepUtilities.isASITransaction(reqInternalFormat)){
				fepResponseCode = FEP_RESPONSE_CODE_FEP_TIMED_OUT;
				extResponseCode = EXTERNAL_RESPONSE_CODE_DO_NOT_HONOR;
			} else {
				if (sendTimeoutResponse) {
					fepResponseCode =  FEP_RESPONSE_CODE_FEP_TIMED_OUT;
				} else {
					fepResponseCode = FEP_RESPONSE_CODE_FEP_TIMED_OUT_BRAND_STANDIN;
				}
			extResponseCode = EXTERNAL_RESPONSE_CODE_57;
			}
			//[rrabaya:11/27/2014]RM#5850 Set correct External Response Code for ASI transaction : End
		} else if (respIsoMsg.getMTI().startsWith(PREFIX_MTI_04)){
			fepResponseCode = FEP_RESPONSE_CODE_APPROVE;
			extResponseCode = FLD39_RESPONSE_CODE_SUCCESS;
			originalFepSeq = getOriginalFepSeq(respIsoMsg);
			originalTransCode = getOriginalTransCode(respIsoMsg);
		}
		
		reqInternalFormat.setValue(HOST_COMMON_DATA.RESPONSE_CODE, extResponseCode);
		reqInternalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, fepResponseCode);
		reqInternalFormat.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER, originalFepSeq);
		reqInternalFormat.setValue(HOST_FEP_ADDITIONAL_INFO.ORIGINAL_TRANSACTION_CODE, originalTransCode);
		
		reqInternalFormat.setValue(HOST_HEADER.MESSAGE_PROCESSING_NUMBER, parameterMessageClone.getFEPSeq());
		
		parameterMessageClone.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
		parameterMessageClone.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);
		parameterMessageClone.setMessageContent(reqInternalFormat);
		
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), parameterMessageClone);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"Sending message to <"
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + parameterMessageClone.getProcessingTypeIdentifier() + 
				"| Data format:" + parameterMessageClone.getDataFormatIdentifier() +
				"| MTI:" + reqInternalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| fepSeq:" + parameterMessageClone.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + reqInternalFormat.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID:" + reqInternalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| DestinationId:" + reqInternalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() + 
				"| FEP response Code:" + reqInternalFormat.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		
		sendMessage(mcpProcess.getQueueName(SysCode.LN_ACE_STORE), parameterMessageClone);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_ACE_STORE) + ">" +
				"[Process type:" + parameterMessageClone.getProcessingTypeIdentifier() + 
				"| Data format:" + parameterMessageClone.getDataFormatIdentifier() +
				"| MTI:" + reqInternalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + parameterMessageClone.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + reqInternalFormat.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + reqInternalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + reqInternalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ reqInternalFormat.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		
		// if sendTimeoutResponseFlag is ON and line status is OK we will send timeout response to Banknet else NO
		if (SysCode.LINE_OK == getLineStatus() && ((respIsoMsg.getMTI().startsWith(PREFIX_MTI_01) && sendTimeoutResponse) 
				|| respIsoMsg.getMTI().startsWith(PREFIX_MTI_04))) {
			
			sendMessage(mcpProcess.getQueueName(LN_HOST_STORE), parameterMessageClone);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "sending a message to <" 
					+ mcpProcess.getQueueName(LN_HOST_STORE) + ">" +
					"[Process type:" + parameterMessageClone.getProcessingTypeIdentifier() + 
					"| Data format:" + parameterMessageClone.getDataFormatIdentifier() +
					"| MTI:" + reqInternalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + parameterMessageClone.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + reqInternalFormat.getValue(
							HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + reqInternalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + reqInternalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| Response Code: "+ reqInternalFormat.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			
			sendTimeoutMsgToBrand(parameterMessageClone, respIsoMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "parameterMessage");
			FepUtilities.writeISOMsgtoAppLog(mcpProcess, (ISOMsg)parameterMessage.getMessageContent());
			
		}
		
		if (respIsoMsg.getMTI().startsWith(PREFIX_MTI_01)) {
			originalProcessDateTime = mcpProcess.getSystime().substring(0,4) + respIsoMsg.getString(7);
			originalFepSeq = parameterMessageClone.getFEPSeq();
			originalTransCode = reqInternalFormat.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue();
		}
		//[rrabaya:11/27/2014]RM#5850 ASI Transaction will not have reversal advice : Start
		if(!FepUtilities.isASITransaction(reqInternalFormat)){
		// Create 0420 ISOMsg
		try {
			reversalIsoMsg = createReversalAdvice(parameterMessage, reqInternalFormat);
			mcpProcess.writeAppLog(Level.DEBUG,	"[BanknetTimeout]: timeout_Issuing(): reversalIsoMsg: " + reversalIsoMsg);
		} catch (IllegalParamException ipe) {
			args[0]  = ipe.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetTimeout] execute(): End Process");
			return;

		} catch (AeonDBException ade) {
			args[0]  = ade.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ade));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetTimeout] execute(): End Process");
			return;

		} catch (ISOException ie) {
			args[0]  = ie.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetTimeout] execute(): End Process");
			return;
		}// End of try catch

		// Create and set new fep sequence number for 0420 Message
		try {
			msgProcessingNum = mcpProcess.getSequence(SysCode.FEP_SEQUENCE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetTimeout]: timeout_Issuing(): msgProcessingNo: " + msgProcessingNum);
			parameterMessage.setFEPSeq(msgProcessingNum);

		} catch (AeonDBException ade) {
			args[0]  = ade.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ade));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetTimeout] execute(): End Process");
			return;

		} catch (IllegalParamException ipe) {
			args[0]  = ipe.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetTimeout] execute(): End Process");
			return;
		}

		// Create 0420 InternalFormat from 0420 ISOMsg
		inFormatProcess = new BanknetInternalFormatProcess(mcpProcess);
		InternalFormat reversalInternalFormat = inFormatProcess.createInternalFormatFromISOMsg(reversalIsoMsg);
		currencyCodeCheck(reversalInternalFormat, reversalIsoMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: timeout_Issuing(): reversalInternalFormat: " + reversalInternalFormat);

		if(null == reversalInternalFormat || SPACE.equals(reversalInternalFormat)){
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout] reversalInternalFormat is null");
		}
		//[msayaboc 07-01-11] Added important fields including timestamp	
		try {
			String timeStamp = mcpProcess.getSystime();
			final String processIOType = PROCESS_I_O_TYPE_ISRES;
			final String processNo = mcpProcess.getProcessName();

			reversalInternalFormat.addTimestamp(processNo, timeStamp, processIOType);

		} catch (SharedMemoryException sme) {
			args[0]  = sme.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout] execute(): End Process");
			return;
		} // End of try catch
		
		reversalInternalFormat.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_DATE_TIME, originalProcessDateTime);
		reversalInternalFormat.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER, originalFepSeq);
		reversalInternalFormat.setValue(HOST_FEP_ADDITIONAL_INFO.ORIGINAL_TRANSACTION_CODE, originalTransCode);
		reversalInternalFormat.setValue(HOST_HEADER.MESSAGE_PROCESSING_NUMBER, ISOUtil.padleft(msgProcessingNum, 7, '0'));
		reversalInternalFormat.setValue(HOST_HEADER.MESSAGE_PROCESSING_DATE_TIME, mcpProcess.getSystime().toString().substring(0, 14));
		reversalInternalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_RESPONSE_CODE_FEP_TIMED_OUT);
		reversalInternalFormat.setValue(HOST_COMMON_DATA.RESPONSE_CODE, NO_SPACE);

		parameterMessage.setMessageContent(reversalIsoMsg);

		// Insert to table 0420 (internal format)
		try {
			insertToLogTable(COL_PROCESS_STATUS_APPROVED, COL_PROCESS_RESULT_APPROVE);

		} catch (Exception e) {
			args[0]  = e.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetTimeout] execute(): End Process");
			return;
		}
		
		/* Removed - Redmine #3873 [mquines: 20110705] Added Response Code = 13042 Time Out
		reversalInternalFormat.setValue(
				CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_RESPONSE_CODE_FEP_TIMED_OUT);
		reversalInternalFormat.setValue(
				HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_DATE_TIME, origProcDateTime);
		reversalInternalFormat.setValue(
				HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER, parameterMessageClone.getFEPSeq());
		*/
		parameterMessage.setMessageContent(reversalInternalFormat);
		parameterMessage.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
		parameterMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);

		/* Send to queues
		 * [JMarigondon 09-23-2011] Return sending to monitoring system
		 * [nvillarete 09/15/2011] : Redmine #3266: 
		 *   removed sending to monitoring since this has been logged when a corresponding adapter already hits timeout.
		 */		
		queueName = mcpProcess.getQueueName(LN_MON_STORE);
		sendMessage(queueName, parameterMessage);

		//[MQuines:06-24-2011]
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"sending a message to <" + queueName + ">" +
				"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + parameterMessage.getDataFormatIdentifier() +
				"| MTI:" + reversalInternalFormat.getValue(
						OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + parameterMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + reversalInternalFormat.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + reversalInternalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + reversalInternalFormat.getValue(
						HOST_HEADER.DESTINATION_ID).getValue() +
				"| Response Code: "+ reversalInternalFormat.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

		queueName = mcpProcess.getQueueName(LN_HOST_STORE);
		sendMessage(queueName, parameterMessage);
		// [MAguila: 20120620] Redmine 3876 Added logging for Original FepSeq
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"sending a message to <" + queueName + ">" +
				"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + parameterMessage.getDataFormatIdentifier() +
				"| MTI:" + reversalInternalFormat.getValue(
						OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + parameterMessage.getFEPSeq() +
				"| Original fepSeq: " + reversalInternalFormat.getValue(
						HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER).getValue() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + reversalInternalFormat.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + reversalInternalFormat.getValue(
						HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + reversalInternalFormat.getValue(
						HOST_HEADER.DESTINATION_ID).getValue() +
				"| Response Code: "+ reversalInternalFormat.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

		queueName = mcpProcess.getQueueName(LN_ACE_STORE);
		sendMessage(queueName, parameterMessage);

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"sending a message to <" + queueName + ">" +
				"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + parameterMessage.getDataFormatIdentifier() +
				"| MTI:" + reversalInternalFormat.getValue(
						OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + parameterMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" 
				+ reversalInternalFormat.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + reversalInternalFormat.getValue(
						HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + reversalInternalFormat.getValue(
						HOST_HEADER.DESTINATION_ID).getValue() +
				"| Response Code: "+ reversalInternalFormat.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		}
		//[rrabaya:11/27/2014]RM#5850 ASI transaction will not have reversal advice : End
	}// End of timeout_Issuing()
	
	private String getOriginalTransCode(ISOMsg isoMsg) {

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout] Set Original Message Processing Number");
		String transactionCode = NO_SPACE;
		
		String field3_1 = isoMsg.hasField(3) ? isoMsg.getString(3).substring(0, 2) : NO_SPACE;
		int field4 = isoMsg.hasField(4) ? Integer.parseInt(isoMsg.getString(4)) : 0;
		int field5 = (isoMsg.hasField(5)) ? Integer.parseInt(isoMsg.getString(5)) : 0;
		String field49 = (isoMsg.hasField(49)) ? isoMsg.getString(49) : NO_SPACE;
		
		if((Constants.FLD3_1_TRANSACTION_TYPE_00).equals(field3_1)) {
			if(((Constants.ONEDOLLAR == field4) || Constants.ONEDOLLAR == field5) 
					&& Constants.FLD49_TRANSACTION_CURRENCY_CODE_840.equals(field49)){
				transactionCode =  Constants.TRANS_CODE_010103;
			} else {
				transactionCode = Constants.TRANS_CODE_010101;
			}
		} else if((Constants.FLD3_1_TRANSACTION_TYPE_01).equals(field3_1) 
				|| (Constants.FLD3_1_TRANSACTION_TYPE_17).equals(field3_1)
				|| (Constants.FLD3_1_TRANSACTION_TYPE_28).equals(field3_1) ) {
			transactionCode = Constants.TRANS_CODE_010301;
		} else if((Constants.FLD3_1_TRANSACTION_TYPE_30).equals(field3_1)) {
			transactionCode = Constants.TRANS_CODE_010403;
		} else if((Constants.FLD3_1_TRANSACTION_TYPE_20).equals(field3_1)) {
			transactionCode = Constants.TRANS_CODE_090101;
		}
		
		return transactionCode;
	}

	/**
	 * Helper function to get original fepSeq No.
	 * @param authIsoMsg ISOMsg
	 * @throws ISOException
	 * @throws Exception
	 */
	private String getOriginalFepSeq (ISOMsg authIsoMsg) throws ISOException, Exception {
		String originalFepSeq = NO_SPACE;
		List<FEPT022BnkisDomain> listResult = new ArrayList<FEPT022BnkisDomain>();
		domain = new FEPT022BnkisDomain();
		if (authIsoMsg.hasField(90)) {
			String field90 = authIsoMsg.getString(90);
			String searchKey = field90.substring(0, 4) + authIsoMsg.getValue(2) + field90.substring(10, 20) 
			+ field90.substring(4, 10) + field90.substring(20, 31) + authIsoMsg.getString(37);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout] searchKey: " + searchKey);
			domain.setNw_key(searchKey);
			listResult = dao.findByCondition(domain);
			if (null != listResult && !listResult.isEmpty()) {
				originalFepSeq = listResult.get(0).getFep_seq().toString();
			} else {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[Base1Timeout] No Original FepSeq Found.");
			}
		}
		return originalFepSeq;
	}

	/**
	 * Helper function for inserting to issuing log table
	 * @param status The processing status
	 * @param result The process result
	 * @throws Exception
	 */
	private void insertToLogTable(String status, String result) throws Exception {

		String issuingLogKey;
		String mti;
		String field2;
		String field7;
		String field11;
		String field32;
		String field37 = NO_SPACE;
		String busDate;

		/*
		 * Use the internal message to get the issuing log key The issuing log
		 *  key is used for updating the issuing log table
		 */
		ISOMsg isoMessageContent = (ISOMsg) parameterMessage.getMessageContent();

		mti = isoMessageContent.getMTI();
		field2 = (isoMessageContent.getString(2).trim().equals(NO_SPACE) 
				? NO_SPACE : isoMessageContent.getString(2));
		field7 = isoMessageContent.getString(7);
		field11 = isoMessageContent.getString(11);
		field32 = (MTI_0190.equals(mti) ? NO_SPACE 
				: ISOUtil.padleft(isoMessageContent.getString(32), 11, cPAD_0));
		field37 = (isoMessageContent.getString(37).trim().equals(NO_SPACE) 
				? ISOUtil.padleft(field37, 12, cPAD_0) : isoMessageContent.getString(37));

		issuingLogKey = mti + field2 + field7 + field11 + field32 + field37;
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable(): issuingLogKey: " 
				+ mti + field2 + field7 + field11 + field32 + field37);

		busDate = ISODate.formatDate(new Date(), DATE_FORMAT_yyyyMMdd);

		/*
		 * Set the following fields in domain: Network Key, FEP Sequence,
		 * Business Date, Process Status Process Result, Message Data 
		 * Then insert the updated values into the log table
		 */
		domain.setNw_key(issuingLogKey);
		domain.setFep_seq(new BigDecimal(parameterMessage.getFEPSeq()));
		domain.setPro_status(status);
		domain.setPro_result(result);
		domain.setUpdateId(mcpProcess.getProcessName());
		domain.setBus_date(busDate);
		domain.setMsg_data(FepUtilities.getBytes(isoMessageContent));

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable() : Inserting to Issuing Log table:");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable() : Network key: " + domain.getNw_key());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable() : FEP seq: " + domain.getFep_seq());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable() : Status: " + domain.getPro_status());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable() : Result: " + domain.getPro_result());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable() : Update ID: " + domain.getUpdateId());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable() : Business date: " + domain.getBus_date());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: insertToLogTable() : Msg data: " + domain.getMsg_data());

		//[MQuines:12/21/2011] Added try catch handling
		try{
			dao.insert(domain);		
	 		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
	 				"[BanknetTimeout] Successfull in Domain Insert");
	 		transactionManager.commit();
	 		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
	 				"[BanknetTimeout] Successfull in Commit");
	 		
		}catch (Exception e) {
			try {
	 			transactionManager.rollback();
	 			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
	 					"[BanknetTimeout] Rollback Occurs");
	 		 
	 		} catch (SQLException se) {  			 
				 args[0]  = se.getMessage();
				 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, 
						 EXCEPTION_DB_OPERATION_SAW2154);
				 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, 
						 EXCEPTION_DB_OPERATION_SAW2154, args);
				 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
						 FepUtilities.getCustomStackTrace(se));
				 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
						 "[BanknetTimeout] Nothing to Rollback");
	 		 } // End of try catch
	         
			 args[0]  = e.getMessage();
			 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007);
			 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args);
			 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetTimeout] execute(): End Process");
			 throw e;
		}// End of try catch
		
	}// End of insertToLogTable()

	/**
	 * Helper function to create reversal ISOMsg
	 * @param message The received Fep Message which contains ISOMsg
	 * @param internalMsg Internal Message which contains original MTI
	 * @return ISOMsg The reversal ISOMsg created
	 * @throws ISOException
	 * @throws AeonDBException
	 * @throws IllegalParamException
	 */
	private ISOMsg createReversalAdvice(FepMessage message, InternalFormat internalMsg) throws 
	ISOException, AeonDBException, IllegalParamException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetTimeout]: createReversalAdvice() start");
		
		int mti0100Fields[] = {18, 61};
		String field90 = NO_SPACE;
		ISOMsg reversalIsoMsg = (ISOMsg) message.getMessageContent();
		String originalMTI = internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue();
		
		if (originalMTI.startsWith("01")) {
			reversalIsoMsg.unset(mti0100Fields);

			// [mqueja:09/06/2011] [added field90 for reference of 0420 to be able to acquire org_stan]
			field90 = originalMTI + reversalIsoMsg.getString(11) + reversalIsoMsg.getString(7) + 
			ISOUtil.padleft((reversalIsoMsg.hasField(32)) ? reversalIsoMsg.getString(32) : NO_SPACE, 11, cPAD_0) +
			ISOUtil.padleft((reversalIsoMsg.hasField(33)) ? reversalIsoMsg.getString(33) : NO_SPACE, 11, cPAD_0);

			reversalIsoMsg.set(90, field90);

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetTimeout]: createReversalAdvice(): Field90: " + field90);
		}
		
		// create new STAN and transaction date and time for Reversal Message.	
		reversalIsoMsg.setMTI(SysCode.MTI_0420);

		reversalIsoMsg.set(7, ISODate.formatDate(new Date(), Constants.DATE_FORMAT_MMDDhhmmss));

		reversalIsoMsg.set(11, ISOUtil.padleft(
				(String)mcpProcess.getSequence(SysCode.STAN_SYS_TRACE_AUDIT_NUMBER).trim(), 6, '0'));

		reversalIsoMsg.set(12, ISODate.formatDate(new Date(), Constants.DATE_FORMAT_hhmmss_24H));

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: createReversalAdvice(): createReversalMsg F7: " + reversalIsoMsg.getString(7));
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: createReversalAdvice(): createReversalMsg F11: " + reversalIsoMsg.getString(11));
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetTimeout]: createReversalAdvice(): createReversalMsg F12: " + reversalIsoMsg.getString(12));

		reversalIsoMsg.recalcBitMap();

		return reversalIsoMsg;
	}// End of createReversalAdvice()    

	/**
	 * Helper function to send Response to Banknet on Timeout if Flag is ON
	 * @param parameterMessageClone Copy of the original FepMessage
	 * @param respIsoMsg Copy of the request ISOMsg
	 * @throws ISOException
	 */
	private void sendTimeoutMsgToBrand(FepMessage parameterMessageClone, ISOMsg respIsoMsg) throws ISOException {
		GenericValidatingPackager packager = new GenericValidatingPackager(getPackageConfigurationFile());
		int mti0110Fields[] = {12, 13, 14, 18, 22, 23, 26, 35, 52, 53, 55, 42, 43, 61};
		int mti0410Fields[] = {12, 13, 14, 18, 22, 23, 26, 35, 38, 42, 43, 45, 
				52, 53, 54, 55, 60, 61, 102, 103, 112, 120, 121, 123, 124, 125, 127};
		int field48SubFields[] = {20, 32, 33, 47, 58, 74};
		ISOMsg field48Component;
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetTimeout] timeout_Issuing(): Response will now be sent due to Timeout");

		respIsoMsg.setResponseMTI();
		if(MTI_0110.equals(respIsoMsg.getMTI())){
			respIsoMsg.unset(mti0110Fields);
			//[rrabaya:11/27/2014]RM#5850 Set valid decline response code for ASI transaction : Start
			InternalFormat interMsg = parameterMessageClone.getMessageContent();
			if(FepUtilities.isASITransaction(interMsg)){
				respIsoMsg.set(39, EXTERNAL_RESPONSE_CODE_DO_NOT_HONOR);
			} else {
				respIsoMsg.set(39, FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57);
			}
			//[rrabaya:11/27/2014]RM#5850 Set valid decline response code for ASI transaction : End
		} else {
			respIsoMsg.unset(mti0410Fields);
			respIsoMsg.set(39, FEP_BNK_EXTERNAL_CODE_APPROVED_00);

			// Field 48 Unset Fields: (0400)
			if (respIsoMsg.hasField(48)) {
				field48Component = (ISOMsg) respIsoMsg.getValue(48);
				for (int index : field48SubFields) {
					field48Component.unset(index);
				}
			}
		}

		respIsoMsg.setPackager(packager);
		respIsoMsg.recalcBitMap();
		byte[] messageArray = pack(respIsoMsg);

		parameterMessageClone.setProcessingTypeIdentifier(QH_PTI_MCP_RES);
		parameterMessageClone.setDataFormatIdentifier(QH_DFI_ORG_MESSAGE);
		parameterMessageClone.setMessageContent(messageArray);

		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), parameterMessageClone);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <"+
				mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process type:" + parameterMessageClone.getProcessingTypeIdentifier() +
				"| Data format:" + parameterMessageClone.getDataFormatIdentifier() +
				"| MTI:" + respIsoMsg.getMTI() +
				"| fepSeq:" + parameterMessageClone.getFEPSeq() +
				"| NetworkID: BNK" +
				"| Processing Code(first 2 digits):" + respIsoMsg.getString(3).substring(0,2) +
				"| Response Code: " + (
						(respIsoMsg.hasField(39)) ? respIsoMsg.getString(39) : "Not Present" + "]"));
	}// end of timeoutResponseCheck()
}// End of class