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
 */

package com.aeoncredit.fep.core.adapter.brand.banknet.mcp;

import static com.aeoncredit.fep.core.adapter.brand.common.Constants.*;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Level;
import org.jpos.iso.ISODate;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;

import com.aeoncredit.fep.common.SysCode;
import com.aeoncredit.fep.core.adapter.brand.common.Constants;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.internalmessage.FepMessage;
import com.aeoncredit.fep.core.internalmessage.InternalFormat;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.EDCShoppingFieldKey.EDC_SHOPPING;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.CONTROL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_COMMON_DATA;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_FEP_ADDITIONAL_INFO;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_HEADER;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.OPTIONAL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey;
import com.aeoncredit.fep.framework.dbaccess.FEPT015BnkaqDAO;
import com.aeoncredit.fep.framework.dbaccess.FEPT015BnkaqDomain;
import com.aeoncredit.fep.framework.dbaccess.FEPTransactionMgr;
import com.aeoncredit.fep.framework.exception.AeonException;
import com.aeoncredit.fep.framework.exception.DBNOTInitializedException;
import com.aeoncredit.fep.framework.exception.IllegalParamException;
import com.aeoncredit.fep.framework.exception.SharedMemoryException;
import com.aeoncredit.fep.framework.log.LogOutputUtility;
import com.aeoncredit.fep.framework.mcp.IMCPProcess;

/**
 * Business logic for banknet acquiring response messages
 */
public class BanknetAcResponse extends BanknetCommon {
    
    private FEPT015BnkaqDAO dao;
    
    private FEPT015BnkaqDomain domain;
    
    /** From execute */
    private ISOMsg isoMsg; 
    
    /** From shared memory */
    private InternalFormat internalMessage; 
        
    /** Transaction Manager */
    private FEPTransactionMgr transactionMgr;
    
    /** Variables used for Exception Handling*/
	private String[] args   = new String[2];
	private String sysMsgID  = NO_SPACE;
    
	/**
	 * BanknetAcResponse constructor.
	 * @param mcpProcess The instance of AeonProcessImpl
	 */
    public BanknetAcResponse(IMCPProcess mcpProcess) {
        super(mcpProcess);
        
        try {
            domain = new FEPT015BnkaqDomain(); 
            dao = new FEPT015BnkaqDAO(mcpProcess.getDBTransactionMgr());
        } catch (DBNOTInitializedException dnie) {
        	args[0]  = dnie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(dnie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse]: End Process");
			return;
        }
    }// End of BanknetAcResponse()
    
	/**
	 * Business logic of the class for executing the Banknet response for
	 * acquiring messages. Receives ISOMsg and is supposed to send to FEP
	 * Processing
	 * @param msg The Fep Message received from Dispatcher
	 **/
    public void execute(FepMessage msg) {
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() start");
        FepMessage sharedFepMessage;
        FepMessage fepMessage;
        String dataFormatId;
        String searchKey = NO_SPACE;
        String mti = NO_SPACE;
        String originalFepSequence = NO_SPACE;
        String searchNwKey;
        String transactionCode = null;
        String queueName;
        List<FEPT015BnkaqDomain> results = null;
        FEPT015BnkaqDomain updateDomain = null;
        FEPT015BnkaqDomain searchDomain;
        FEPT015BnkaqDomain domainToUpdate;
        InternalFormat internalMsg;
        
        // Retrieve FEPSeq from acquiring log table
        String fepSeq = null;
        
        try {
        	isoMsg = msg.getMessageContent();
            
            searchDomain = new FEPT015BnkaqDomain();
            searchNwKey = getNetworkKeySearchingRequest(msg);
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : searchNwKey is " + searchNwKey);
            
            searchDomain.setNw_key(searchNwKey);
            searchDomain.getFep_seq();
            results = dao.findByCondition(searchDomain);
            
            if (null == results || results.isEmpty()) {
                mcpProcess.writeAppLog(Level.ERROR, "[BanknetAcResponse] : execute() : resultList is null or empty.");
                return;
            }
            
            fepSeq = results.get(0).getFep_seq().toString();
            fepSeq = ISOUtil.padleft(fepSeq, 7, cPAD_0);
            
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "receiving a message from <BNKLCP>" + 
    				"[Process Type: " + msg.getProcessingTypeIdentifier() + 
    				"| Data Format: " + msg.getDataFormatIdentifier() +
    				"| MTI: " + isoMsg.getMTI() + 
    				"| fepSeq: " + msg.getFEPSeq() + 
    				"| Network ID: BNK" + 
    				"| Processing Code(first 2 digits): " + ((isoMsg.hasField(3)) ? 
    						(isoMsg.getString(3).substring(0, 2)) : "Not Present")  +
    				"| Response Code: " + (
    						(isoMsg.hasField(39)) ? isoMsg.getString(39) : "Not Present") + "]");
            
        } catch (Throwable th) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(th));
            mcpProcess.error("[BanknetAcResponse] : " + th.getMessage(), th);
            return;
        }
        
        // Read the Shared Memory for finding the request message
        sharedFepMessage = getTransactionMessage(fepSeq);
        // [MAguila: 20120619] Redmine #3860 Updated Logging
        if(null == sharedFepMessage) {
        	sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, CLIENT_SYSLOG_INFO_2129);
        	mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_WARNING, CLIENT_SYSLOG_INFO_2129, null);   
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : " +
        	"sharedFepMessage is null. Return from execute()");
        	return;
        }
        
        // [lsosuan:01/31/2011] [Redmine#2184] [Set LogSequence]
        mcpProcess.setLogSequence(sharedFepMessage.getLogSequence());
        
        dataFormatId = sharedFepMessage.getDataFormatIdentifier();
        
        try {
            transactionMgr = mcpProcess.getDBTransactionMgr();
            
        } catch (DBNOTInitializedException dnie) {
        	args[0]  = dnie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(dnie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse]: End Process");
			return;
        }
        
        try {
        	// set packager and get mti
            isoMsg.setPackager(packager);
            mti = isoMsg.getMTI();
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : mti : " + mti);
           
        }catch (ISOException ie) {
        	args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcRequest] : execute() End Process");
			return;
        }
        
        /*
         * Check the message format according to the MTI.
         *  call CommonBrandMCP.validate(isoMsg); -1 = no errors
         */
         int result = validate(isoMsg);
         mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : validateResult : " + result);
          
         // If error occurs
         if(result != VALIDATE_NO_ERROR) {
         	sysMsgID = mcpProcess.writeSysLog(Level.WARN, CLIENT_SYSLOG_SCREEN_2128);
            mcpProcess.writeAppLog(sysMsgID, Level.ERROR, CLIENT_SYSLOG_SCREEN_2128, null);
            mcpProcess.writeAppLog(Level.WARN, "[BanknetAcResponse] : execute() : Validate error. " +
                    "End Process");                               
            return;
         }
         
         //[emaranan:03/23/2011] [Redmine#2258] Add filter message in shared memory.
         if(SysCode.QH_DFI_FEP_MESSAGE.equals(sharedFepMessage.getDataFormatIdentifier())) {
        	 
        	 // [emaranan:03/21/2011] Delete line: internalMsg = sharedFepMessage.getMessageContent();
        	 internalMessage = sharedFepMessage.getMessageContent();
        	 transactionCode = internalMessage.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue();
        	 
         }
         
         mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : transaction code : " + transactionCode);
            
         // General try catch
         try{
        	 if (null != transactionCode) {
            	
        		/*
            	 * [lsosuan:02/18/2011] [Redmine#2209] [[BKN-ACQ] BanknetAcResponse] added "04XXXX"
            	 * [emaranan:03/18/2011] [Redmine#2258] Change logic from && to ||
            	 * 
            	 * If acquiring request message is Reversal request message("03XXXX")
            	 *  Call getKeyForSearchOriginal method to get the key for searching original message
            	 *  (use Message Data Request Message Internal Format as param)
            	 * 
            	 */
                if (PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_REQ_1.equals(transactionCode.substring(0, 2)) || 
                        PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE.equals(transactionCode.substring(0, 2))) {
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Reversal request ");
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                    		"NW key searching original: " + searchKey);
                   
                    domain = new FEPT015BnkaqDomain();
                    searchKey = getNetworkKeySearchingOriginal(sharedFepMessage);
                    domain.setNw_key_searching_original(searchKey);
                    results = dao.findByCondition(domain);
                    
                    /*
                     *     If record = 0 (no original transaction)
                     *         Send the decline message to FEP
                     *         End the process
                     * 
                     *     If record = 1
                     *         If status = "4" (Reversal is in progress)
                     *             Call FEPT015BnkaqDAO.update() to update the status of the
                     *             record to "5" (REVERSAL OK) then go to step 4 
                     *             (If DFI is internal format...)
                     *     
                     *         If status is others
                     *             Send the decline message to FEP
                     *             End process
                     */
                    if (null == results || results.isEmpty()) {
                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                                "record not found.");

                        sharedFepMessage = editMsg(sharedFepMessage);
                        internalMsg = sharedFepMessage.getMessageContent();
                        internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, FEP_BNK_RESPONSE_CODE_INVALID_TRANSACTION);
                        sharedFepMessage.setMessageContent(internalMsg);
                        sharedFepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
                        sharedFepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                        queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
                        sendMessage(queueName, sharedFepMessage);
                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
        						+ queueName + ">" +
        						"[Process type:" + sharedFepMessage.getProcessingTypeIdentifier() + 
        						"| Data format:" + sharedFepMessage.getDataFormatIdentifier() +
        						"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
        						"| FepSeq:" + sharedFepMessage.getFEPSeq() +
        						"| NetworkID: BNK" + 
        						"| Transaction Code(service):" + internalMsg.getValue(
        								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
        						"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
        						"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
        						"| FEP Response Code: "+ internalMsg.getValue(
        								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
                        return;
                    }// End of If record = 0
                    
                    else { // If record = 1
                        updateDomain = results.get(0);

                        // [mqueja:03/21/2011] [Redmine#2278] [EDC SS documents update]
                        originalFepSequence  = (ISOUtil.padleft(String.valueOf(updateDomain.getFep_seq()), 7, '0'));
                        
                        // If status = "4"
                        if (COL_PROCESS_STATUS_REVERSAL_IN_PROGRESS.equals(
                                updateDomain.getPro_status())) {
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                                    "Updating process status of original transaction");   
                            // [emaranan:03/18/2011] [Redmine#2258]  Should update original not reversal transaction.
                            
                            byte[] messageArray = pack(isoMsg);
                            
                            // If error occurs,
                            if(messageArray.length < 1) {
                                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse]Error in Packing Message");
                                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse] execute(): End Process");
                                        return;
                            } // End of if bytesMessage is 0 
                            
                            domainToUpdate = new FEPT015BnkaqDomain();
                            domainToUpdate.setPro_status(COL_PROCESS_STATUS_REVERSAL_OK);
                            domainToUpdate.setMsg_data(messageArray);
                            domainToUpdate.setNw_key(updateDomain.getNw_key());
                            domainToUpdate.setUpdateId(mcpProcess.getProcessName());
                            domainToUpdate.setFep_seq(updateDomain.getFep_seq());
                            domainToUpdate.setBus_date(updateDomain.getBus_date());
                            
                            if(isoMsg.getString(39).equals(Constants.FLD39_RESPONSE_CODE_SUCCESS)){
                                domainToUpdate.setPro_result(COL_PROCESS_RESULT_APPROVE);
                            }
                            
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Nw_key : " 
                                    + domainToUpdate.getNw_key());
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Pro_status : " 
                                    + domainToUpdate.getPro_status());
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Pro_result : " 
                                    + domainToUpdate.getPro_result());

                            dao.update(domainToUpdate);
                            transactionMgr.commit();
                        
                        } // End of If status = "4"
                        
                        else { // If status is others
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                                    "Reversal not in progress. Process status: " +
                                    results.get(0).getPro_status());
                            
                            sharedFepMessage = editMsg(sharedFepMessage);
                            internalMsg = sharedFepMessage.getMessageContent();
                            internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, 
                                    FEP_BNK_RESPONSE_CODE_INVALID_TRANSACTION);
                            sharedFepMessage.setMessageContent(internalMsg);
                            sharedFepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
                            sharedFepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                            queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
                            sendMessage(queueName, sharedFepMessage);
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
            						+ queueName + ">" +
            						"[Process type:" + sharedFepMessage.getProcessingTypeIdentifier() + 
            						"| Data format:" + sharedFepMessage.getDataFormatIdentifier() +
            						"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
            						"| FepSeq:" + sharedFepMessage.getFEPSeq() +
            						"| NetworkID: BNK" + 
            						"| Transaction Code(service):" + internalMsg.getValue(
            								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
            						"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
            						"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
            						"| FEP Response Code: "+ internalMsg.getValue(
            								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");                    
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Return from execute()");
                            return;
                        }
                    } // End of  If record = 1
                } // End of If Reversal
                
                /*
                 * [emaranan:02/24/2011] [Redmine#2228] [ACQ] SS documents update
                 * If acquiring request message is Authorization request message (Transaction Code = 01XXXX or 09XXXX)
                 */
                else if (PREFIX_TRANS_CODE_AUTHORIZATION_REQ_1.equals(transactionCode.substring(0, 2))
                        || PREFIX_TRANS_CODE_AUTHORIZATION_REQ_4.equals(transactionCode.substring(0, 2))) {
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Authorization request ");     
                    
                    /*
                     * If acquiring request message is Authorization request
                     *  Search Banknet Acquiring Log Table to get the acquiring request
                     */
                    domain = new FEPT015BnkaqDomain();
                    
                    /*
                     * SearchKey = SysCode.MTI_0100 + pan + amount + stan + terminalId;
                     * [emaranan:11/18/2010] [Redmine#959] [Use NW key from request message]
                     */
                    searchKey = getNetworkKeySearchingRequest(msg);
                    domain.setNw_key(searchKey);
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                    		"NW key searching request: " + searchKey);
                    results = dao.findByCondition(domain);
                    
                    /*
                     * If record = 0 (no request exists)
                     *  Send the decline message to FEP
                     *  End process
                     * If record = 1
                     *  If status = 1 (In Progress)
                     *   Call FEPT015BnkaqDAO.update() to update the status of the
                     *    record to "2" (APPROVE) then go to step 4 (If DFI is internal format...)
                     *         
                     *  If status is others
                     *   Send the decline message to FEP
                     *   End process   
                     */
                    if (null == results || results.isEmpty()) {
                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                                "Search for authorization request in log table returned null");
                        
                        sharedFepMessage = editMsg(sharedFepMessage);
                        internalMsg = sharedFepMessage.getMessageContent();
                        internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, FEP_BNK_RESPONSE_CODE_INVALID_TRANSACTION);
                        sharedFepMessage.setMessageContent(internalMsg);
                        sharedFepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
                        sharedFepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                        queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
                        sendMessage(queueName, sharedFepMessage);
                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
        						+ queueName + ">" +
        						"[Process type:" + sharedFepMessage.getProcessingTypeIdentifier() + 
        						"| Data format:" + sharedFepMessage.getDataFormatIdentifier() +
        						"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
        						"| FepSeq:" + sharedFepMessage.getFEPSeq() +
        						"| NetworkID: BNK" + 
        						"| Transaction Code(service):" + internalMsg.getValue(
        								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
        						"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
        						"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
        						"| FEP Response Code: "+ internalMsg.getValue(
        								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
                        return;
                    }// End of If record = 0
                    
                    else {// If record = 1 
                        updateDomain = results.get(0);
                        
                        // If status = 1 (In Progress)
                        if (COL_PROCESS_STATUS_IN_PROGRESS.equals(
                                updateDomain.getPro_status())) {
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                                    "Updating process status of authorization request");
                            
                            byte[] messageArray = pack(isoMsg);
                            
                            // If error occurs,
                            if(messageArray.length < 1) {
                                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse]Error in Packing Message");
                                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse] execute(): End Process");
                                        return;
                            } // End of if bytesMessage is 0 
                            
                            domainToUpdate = new FEPT015BnkaqDomain();
                            domainToUpdate.setNw_key(updateDomain.getNw_key());
                            domainToUpdate.setFep_seq(updateDomain.getFep_seq());
                            domainToUpdate.setBus_date(updateDomain.getBus_date());
                            domainToUpdate.setPro_status(COL_PROCESS_STATUS_APPROVED);
                            domainToUpdate.setMsg_data(messageArray);

                            /*
                             * [mquines:09/16/2010] [Redmine#1620] [If the Message is approved message 
                             *  (judge by Response Code), then update the Process Result of the record to "0"(Approve)
                             */
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : field 39 : " 
                                    + isoMsg.getString(39));
                            
                            if(isoMsg.getString(39).equals(Constants.FLD39_RESPONSE_CODE_SUCCESS)){
                                domainToUpdate.setPro_result(COL_PROCESS_RESULT_APPROVE);
                            }
     
                            domainToUpdate.setUpdateId(mcpProcess.getProcessName());
                                                    
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Nw_key : " 
                                    + domainToUpdate.getNw_key());
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Fep_seq : " 
                                    + domainToUpdate.getFep_seq());
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Bus_date : " 
                                    + domainToUpdate.getBus_date());
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Pro_status : " 
                                    + domainToUpdate.getPro_status());
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Pro_result : " 
                                    + domainToUpdate.getPro_result());
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : UpdateId : " 
                                    + domainToUpdate.getUpdateId());
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                                    "updateDomain status in auth response : " + updateDomain.getPro_status());
                            dao.update(domainToUpdate);
                            transactionMgr.commit();
                        }// End of If status = 1 (In Progress)
                        
                        else { // If status is others                   
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : " +
                            		"Authorization not in progress. Process status: " + results.get(0).getPro_status());
                            
                            sharedFepMessage = editMsg(sharedFepMessage);
                            internalMsg = sharedFepMessage.getMessageContent();
                            internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, 
                                    FEP_BNK_RESPONSE_CODE_INVALID_TRANSACTION);
                            sharedFepMessage.setMessageContent(internalMsg);
                            sharedFepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
                            sharedFepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                            queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
                            sendMessage(queueName, sharedFepMessage);
                            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
            						+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
            						"[Process type:" + sharedFepMessage.getProcessingTypeIdentifier() + 
            						"| Data format:" + sharedFepMessage.getDataFormatIdentifier() +
            						"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
            						"| FepSeq:" + sharedFepMessage.getFEPSeq() +
            						"| NetworkID: BNK" + 
            						"| Transaction Code(service):" + internalMsg.getValue(
            								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
            						"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
            						"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
            						"| FEP Response Code: "+ internalMsg.getValue(
            								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
                            return;
                        } // End of If status is others
                    }// End of If record = 1 
                } // End of If acquiring request message is Reversal request message("01XXXX")
            }// End of null checking for transaction code
        	 
        }catch (ISOException ie) {
        	args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsRequest] : execute() : End Process");
            try {
            	transactionMgr.rollback();
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Transaction rollback "); 
            } catch (SQLException e1) {
                mcpProcess.writeSysLog(Level.ERROR, "SQLException");
                mcpProcess.writeAppLog(Level.ERROR, "[BanknetAcResponse] : execute(): SQLException : " + e1.getMessage());
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Transaction rollback fail");
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(ie));
            }
            return;
            
        }catch(Exception e) {
            try {
                mcpProcess.getDBTransactionMgr().rollback();
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Transaction rollback ");
                
            } catch (DBNOTInitializedException dnie) {
            	args[0]  = dnie.getMessage();
    			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
    			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, CLIENT_SYSLOG_DB_7002, args);
    			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(dnie));
    			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse] : execute() : End Process");
    			return;
                
            } catch (SQLException sqle) {
            	args[0]  = sqle.getMessage();
    			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
    			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
    			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(sqle));
    			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse] : execute() : End Process");
            }
            return;
        }
        
        // If data format identifier = 01 (Internal Format)
        if (SysCode.QH_DFI_FEP_MESSAGE.equals(dataFormatId)) {
            try {
                // InternalFormat from FepMessage (shared memory)
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() :" +
                		" Shared memory message is internal format ");
                internalMessage = sharedFepMessage.getMessageContent();
                
                if(null != (originalFepSequence) || !NO_SPACE.equals(originalFepSequence)) {
                	internalMessage.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER, originalFepSequence);
                	//  mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : FepSequence : " 
                	//          + internalMessage.getValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER).getValue());
                }
                sharedFepMessage = editMsg(sharedFepMessage);
                
                 /*
                 * Edit queue header 
                 * Processing type identifier = "22"
                 * Data format identifier = "01"
                 * Send InternalFormat to the queue of FEP processing function
                 */
                sharedFepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                sharedFepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
                
                queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
                sendMessage(queueName, sharedFepMessage);
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
						+ queueName + ">" +
						"[Process type:" + sharedFepMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + sharedFepMessage.getDataFormatIdentifier() +
						"| MTI:" + internalMessage.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + sharedFepMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + internalMessage.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalMessage.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalMessage.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalMessage.getValue(
								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");                    
                
            } catch (Exception e) {
            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e));
            	
                // If error occurs, output system log
                if(e instanceof SharedMemoryException || e instanceof ISOException) {
                    args[0] = e.getMessage();
                    args[1] = e.getLocalizedMessage();
                    sysMsgID= mcpProcess.writeSysLog(Level.ERROR, CLIENT_SYSLOG_SCREEN_2130);
                    mcpProcess.writeAppLog(sysMsgID, Level.ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
                    
                } else {
                    mcpProcess.writeSysLog(Level.ERROR, "Exception: " + e.getMessage());
                    mcpProcess.writeAppLog(Level.ERROR, "Exception : " + e.getMessage());
                }
                
                try {
                	
                    // Update the Log Acquiring Table, use the search result
                    updateDomain.setNw_key(searchKey);
                    updateDomain.setPro_result(COL_PROCESS_RESULT_DECLINE);
                    updateDomain.setUpdateId(mcpProcess.getProcessName());
                    
                    // No need to set this, update process result only updateDomain.setMsg_data(pack(isoMsg));
                    dao.update(domain);
                    transactionMgr.commit();
                    
                }catch(Exception e1) {
                	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e1));
                    mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, "Exception: " + e1.getMessage());
                    
                    try {
                        transactionMgr.rollback();
                        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : Transaction rollback ");
                    } catch (SQLException e2) {
                    	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e));
                        mcpProcess.writeSysLog(Level.ERROR, "[BanknetAcResponse] : execute() : SQLException : Error in transaction rollback " 
                        		+ e2.getMessage());
                    }
                    
                    mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse] : execute() : " + e1.getMessage());
                    mcpProcess.writeAppLog(Level.ERROR, "[BanknetAcResponse] : execute() : Exception: " + e1.getMessage());
                }
                
                /*
                 * Edit queue header 
                 *  Processing type identifier = "22"
                 *  Data format identifier = "01"
                 *  
                 * Create decline message
                 * Send message to the queue of FEP processing function(decline)
                 */
                sharedFepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
                sharedFepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
                
                internalMessage.setValue(HOST_COMMON_DATA.RESPONSE_CODE, FLD39_RESPONSE_CODE_INVALID_TRANSACTION);
                sharedFepMessage.setMessageContent(internalMessage);
                queueName = mcpProcess.getQueueName(SysCode.LN_FEP_MCP);
                sendMessage(queueName, sharedFepMessage);// Issue #420

                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
						+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
						"[Process type:" + sharedFepMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + sharedFepMessage.getDataFormatIdentifier() +
						"| MTI:" + internalMessage.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + sharedFepMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + internalMessage.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalMessage.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalMessage.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalMessage.getValue(
								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
                return;
            }
            
        } else if(SysCode.MTI_0410.equals(mti)) {
        	fepMessage = new FepMessage();
           
        	/*
             * Edit the queue header
             *  Processing Type Identifier = "06"
             *  Data Format Identifier = "10"
             *  
             * Send the message to the queue of FEP processing function
             *  fepMessage currently contains isoMsg, need to convert this to byte[]
             */
             fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_DEL);
             fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);
             // Lquirona 03/25/2011 - For Redmine 2279 - Set fep sequence retrieved from execute
             fepMessage.setFEPSeq(fepSeq);
             
             byte[] messageArray = pack(isoMsg);
             
             // If error occurs,
             if(messageArray.length < 1) {
                 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse]Error in Packing Message");
                 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetAcResponse] execute(): End Process");
                 return;
             } // End of if bytesMessage is 0

             
        	 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : execute() : mti : " + mti);            	 
        	 fepMessage.setMessageContent(messageArray);
             
             queueName = mcpProcess.getQueueName(SysCode.LN_BNK_FORWARD);
             sendMessage(queueName, fepMessage);                     
             mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
						+ mcpProcess.getQueueName(SysCode.LN_BNK_FORWARD) + ">" +
						"[Process type:" + sharedFepMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + sharedFepMessage.getDataFormatIdentifier() +
						"| MTI:" + internalMessage.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + sharedFepMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + internalMessage.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalMessage.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalMessage.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalMessage.getValue(
								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
             return;
        }

        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
   			 "********** BRAND MCP ACQUIRING RESPONSE END PROCESS (Seq="+ sharedFepMessage.getLogSequence() + ")**********");
    }// End of execute()
    
	/**
	 * FepMessage that would have its message contents updated according to the ISOMsg.
	 * It would turn the message contents from ISOMsg to InternalFormat
	 * @param sharedMemoryMessage The Fep Message retrieved from shared memory
	 * @return FepMessage with updated message contents
	 * @throws ISOException
	 * @throws SharedMemoryException
	 * @throws AeonException
	 */
    private FepMessage editMsg(FepMessage sharedFepMessage) throws  SharedMemoryException, ISOException, AeonException {
    	String systime;
    	String respCode;
    	/*
         * Map Time_stamp_part
         *  Process Number (FEXC00101) 
         *  Time Stamp (YYYYMMDDhhmmsssss)
         *  Process I/O Type (if ac request = 0 | if ac response = 1)
         */
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() start");
        systime = mcpProcess.getSystime();
        internalMessage.addTimestamp(
        		mcpProcess.getProcessName(), mcpProcess.getSystime(), SysCode.TIME_STAMP_TYPE_INPUT);
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : Systime : " + systime);
        mcpProcess.writeAppLog(
        		LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : processName : " + mcpProcess.getProcessName());
        
        // REQUEST/RESPONSE Indicator (if response = 2)
        internalMessage.setValue(HOST_HEADER.REQUEST_OR_RESPONSE_INDICATOR, HOST_RESPONSE);

        // Authorization Judgment Division (if acquiring = 4)
        internalMessage.setValue(HOST_HEADER.AUTHORIZATION_JUDGMENT_DIVISION, AUTHORIZATION_JUDGMENT_DIVISION_4);
        
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : REQUEST_OR_RESPONSE_INDICATOR: " 
				+ internalMessage.getValue(HOST_HEADER.REQUEST_OR_RESPONSE_INDICATOR));
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : AUTHORIZATION_JUDGMENT_DIVISION: " 
				+ internalMessage.getValue(HOST_HEADER.AUTHORIZATION_JUDGMENT_DIVISION));
        
        /*
         * [nvillarete:08/25/2010] [Redmine#935] [Include field 71]
         * [ccalanog:10/19/2010] [Redmine#1733] [Set ATM TRANS SEQ to Field 11, if SOURCE is ATM]
         * [salvaro:10/10/2011] [Redmine 3388] Edited handling of Setting the System Trace Audit Number if Source is ATM 
         * so as not to override the STAN value in Internal Format for EDC
         */
        
          if (isoMsg.hasField(11) ) {
        	  if (HOST_HEADER.SOURCE_ID.equals(SysCode.NETWORK_ATM)){
        		  // [emaranan:02/25/2011] [Redmine#2210] [ACQ] STAN value in MTI 0400 Processing
                  internalMessage.setValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER, isoMsg.getString(11));
                  mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : SYSTEM_TRACE_AUDIT_NUMBER: " 
      					+ internalMessage.getValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER)); 
        	  }
        }
        
        // BIT 84 - Authorization ID Response: If Field38:RESPONSE CODE is BitON, set Field38:RESPONSE CODE Issue #2164
		if (isoMsg.hasField(38)) {
			internalMessage.setValue(HOST_COMMON_DATA.AUTHORIZATION_ID_RESPONSE, isoMsg.getString(38));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : AUTHORIZATION_ID_RESPONSE: " 
					+ internalMessage.getValue(HOST_COMMON_DATA.AUTHORIZATION_ID_RESPONSE));
		} // End of f38 checking
        
         // Response Code
        if(isoMsg.hasField(39)) {
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : field39 : " 
                    + isoMsg.getString(39)); 
            
            // [lquirona:17/08/2010] [Redmine#891] [DD update]
            respCode = changeToFEPCode(OUTRESCNV_NW_CODE_BANKNET, isoMsg.getString(39));
            if(null != respCode){
            	
                 // [lquirona:17/08/2010] [Redmine#891] [DD update]
                internalMessage.setValue(HOST_COMMON_DATA.RESPONSE_CODE,isoMsg.getString(39));
                internalMessage.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, respCode);
                internalMessage.setValue(BANKNET_ISSFileUpdKey.RESPONSE_CODE, isoMsg.getString(39));
                
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : HOST COMMON RESPONSE_CODE: " 
    					+ internalMessage.getValue(HOST_COMMON_DATA.RESPONSE_CODE));
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : FEP_RESPONSE_CODE: " 
    					+ internalMessage.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE));
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : BANKNET RESPONSE_CODE: " 
    					+ internalMessage.getValue(BANKNET_ISSFileUpdKey.RESPONSE_CODE));
                  
                
            }else{
                mcpProcess.writeAppLog(Level.ERROR, "[BanknetAcResponse] : editMsg() : Converted fep_resp_code is null ");
                throw new AeonException("Converted fep_resp_code is null");
            }
        }

        /*
         * Message Type ID
         * [lsosuan:12/20/2010] [Redmine#2079] [Banknet MCP shouldn't Change the MTI of EDC Shopping]
         * [lsosuan:12/27/2010] [Redmine#2109] [[BANKNET-ACQ]Item setting is not right]
         */
        internalMessage.setValue(OPTIONAL_INFORMATION.MESSAGE_TYPE, isoMsg.getMTI());

        // Forwarding Institution Country Code
        if(isoMsg.hasField(33)) {
            internalMessage.setValue(
            		BANKNET_ISSFileUpdKey.FORWARDING_INSTITUTION_IDENTIFICATION_CODE, isoMsg.getString(33));
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : FORWARDING_INSTITUTION_IDENTIFICATION_CODE: " 
					+ internalMessage.getValue(BANKNET_ISSFileUpdKey.
							FORWARDING_INSTITUTION_IDENTIFICATION_CODE));
        }
        
        // Network Data
        if(isoMsg.hasField(63)) {
            internalMessage.setValue(BANKNET_ISSFileUpdKey.NETWORK_DATA, isoMsg.getString(63));
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : NETWORK_DATA: " 
					+ internalMessage.getValue(BANKNET_ISSFileUpdKey.NETWORK_DATA));
        }

        // Additional Response Data
        if(isoMsg.hasField(44)) {
            internalMessage.setValue(BANKNET_ISSFileUpdKey.ADDITIONAL_RESPONSE_DATA, isoMsg.getString(44));
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : ADDITIONAL_RESPONSE_DATA: " 
					+ internalMessage.getValue(BANKNET_ISSFileUpdKey.ADDITIONAL_RESPONSE_DATA));
        }
        
        /*
         * [mqueja:06/15/2011] [Redmine issue #2544] [Put value in IC Response Data of internal format]
         * IC Response Data
         */ 
        if(isoMsg.hasField(55)){
        	internalMessage.setValue(InternalFieldKey.IC_INFORMATION.IC_RESPONSE_DATA, isoMsg.getString(55));
            
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : IC_RESPONSE_DATA: " 
					+ internalMessage.getValue(BANKNET_ISSFileUpdKey.ADDITIONAL_RESPONSE_DATA).getValue());
        }
        
        // [salvaro: 10/08/2011] [Added setting of field 90]
        
        if(isoMsg.hasField(90)){
        	internalMessage.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_DATE_TIME, isoMsg.getString(90).substring(10, 20));
        	internalMessage.setValue(HOST_COMMON_DATA.ORG_SYS_TRACE_NO, isoMsg.getString(90).substring(4, 10));
        	
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : ORIGINAL DATE and TIME: " 
					+ internalMessage.getValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_DATE_TIME).getValue());
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() : ORIGINAL STAN: " 
					+ internalMessage.getValue(HOST_COMMON_DATA.ORG_SYS_TRACE_NO).getValue());
        }
        
        /*
         * [lsosuan:12/20/2010] [Redmine#2079] [Banknet MCP shouldn't Change the MTI of EDC Shopping]
         * [lsosuan:12/27/2010] [Redmine#2109] [[BANKNET-ACQ]Item setting is not right]
         * [jfabian:10/22/2010] [Redmine#1753] [Set MESSAGE TYPE ID if source is EDC]
         */
        sharedFepMessage.setMessageContent(internalMessage);
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] : editMsg() end");
        return sharedFepMessage;

    }
	/**
	 * Gets the network key for searching request
	 * @param  msg The Fep Message received which contains ISOMsg
	 * @return String The network key created based on the received ISOMsg
	 * @throws ISOException
	 * @throws IllegalParamException
	 * @throws SharedMemoryException
	 * 
	 */
    private String getNetworkKeySearchingRequest(FepMessage msg) throws ISOException, 
            IllegalParamException, SharedMemoryException{
        String key = NO_SPACE;
        String origMti;
        ISOMsg iso = msg.getMessageContent();
        
        
        origMti = "";
        if(SysCode.MTI_0110.equals(iso.getMTI())){
            origMti = SysCode.MTI_0100;
            
        }else if(SysCode.MTI_0410.equals(iso.getMTI())){
            origMti = SysCode.MTI_0400;
        }
        
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] getNetworkKeySearchingRequest() :  MTI: " + origMti);
        /*
         * Network Key = MTI(The Acquiring Request MTI) 
         *  + Field2:PAN 
         *  + Field11:Systems Trace Audit Number(STAN) 
         *  + "YYYY of GMT 
         *  + Field7:Transmission Date and Time"
         */
        key += origMti;
        key += iso.getString(2);
        key += iso.getString(11);
        key += ISODate.formatDate(Calendar.getInstance().getTime(), DATE_FORMAT_yyyy);
        key += iso.getString(7);
        
        return key;
    }// End of getNetworkKeySearchingRequest
    
    /**
	 * Gets the network key for searching reversal request
	 * @param  msg The Fep Message received which contains Internal Format
	 * @return String The network key created based on the received Internal Format
	 * */
    private String getNetworkKeySearchingOriginal(FepMessage msg){
        String key = NO_SPACE;
        InternalFormat internalFormat = (InternalFormat)msg.getMessageContent();
        String sourceId = internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue();
        
        mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetAcResponse] getNetworkKeySearchingOriginal() : " +
        		"sourceId: " + sourceId);
        
        // If Source ID of Internal Format is "EDC"
        if(SysCode.NETWORK_EDC.equals(sourceId)){
        // Net Work Key Searching Original = HOST_COMMON_DATA.PAN + EDC_SHOPPING.INVOICE_ECR_REF_NO
            key += internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
            key += internalFormat.getValue(EDC_SHOPPING.INVOICE_ECR_REF_NO).getValue();
            
            // If EDC_SHOPPING.MESSAGE_TYPE_ID is "0200", + EDC_SHOPPING.MESSAGE_TYPE_ID
            if(SysCode.MTI_0200.equals(internalFormat.getValue(EDC_SHOPPING.MESSAGE_TYPE_ID).getValue())){
                key += internalFormat.getValue(EDC_SHOPPING.MESSAGE_TYPE_ID).getValue();
                
                // If first two char of HOST_COMMON_DATA.PROCESSING_CODE is "02", + 00
                if(PREFIX_PROCESSING_CODE_02.equals(internalFormat.getValue(HOST_COMMON_DATA.PROCESSING_CODE)
                        .getValue().substring(0, 2))){
                    key += "00";
                }
                
                // If first two char of HOST_COMMON_DATA.PROCESSING_CODE is "22", + 20
                if(PREFIX_PROCESSING_CODE_22.equals(internalFormat.getValue(HOST_COMMON_DATA.PROCESSING_CODE)
                        .getValue().substring(0, 2))){
                    key += "20";
                }
            }
            // If EDC_SHOPPING.MESSAGE_TYPE_ID is "0400", + "0200" + first two char of HOST_COMMON_DATA.PROCESSING_CODE
            else if(SysCode.MTI_0400.equals(internalFormat.getValue(EDC_SHOPPING.MESSAGE_TYPE_ID).getValue())){
                key += "0200";
                key += internalFormat.getValue(HOST_COMMON_DATA.PROCESSING_CODE).getValue().substring(0, 2);  
            }
        }
        
        // If Source ID of Internal Format is "PGW"
        else if(SysCode.NETWORK_PGW.equals(sourceId)){
        	
        // Net Work Key Searching Original = HOST_COMMON_DATA.PAN + HOST_COMMON_DATA.RETRIEVAL_REF_NUMBER
            key = internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
            key += internalFormat.getValue(HOST_COMMON_DATA.RETRIEVAL_REF_NUMBER).getValue();
        }
        
        return key;
    }// End of getNetworkKeySearchingOriginal
    
}// End of class