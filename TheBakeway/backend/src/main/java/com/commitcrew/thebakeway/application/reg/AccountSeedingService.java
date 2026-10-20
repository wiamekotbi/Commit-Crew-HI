package com.commitcrew.thebakeway.application.reg;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//module for the seeding of predefined accounts at startup and when called by operator. Related to UC-REG-03

public class AccountSeedingService {

    private static final Logger logger = LoggerFactory.getLogger(AccountSeedingService.class);

    private final AccountRegistry accountRegistry;
    private final PasswordEncoder passwordEncoder;
    private final SessionManager sessionManager;


    int createdAccountsIndex;
    BakewayAccount[] createdAccounts;
    int errorLogIndex;
    String[] errorLog;

    //constructor
    public AccountSeedingService(AccountRegistry accountRegistry, PasswordEncoder passwordEncoder, SessionManager sessionManager) {
        this.accountRegistry = accountRegistry;
        this.passwordEncoder = passwordEncoder;
        this.sessionManager = sessionManager;
        createdAccounts = new BakewayAccount[100];
        errorLog = new String[100];
        createdAccountsIndex = 0;
        errorLogIndex = 0;
    }

    //adds an error message to the array for later logging.
    public void logInvalidEntry(String errorString){
        errorLog[errorLogIndex] = errorString;
        logger.info(errorString);
        errorLogIndex++;
    }

    //returns true if a line is invalid
    public boolean testLineValidity(String attribute, int lineCounter, String errorString){
            if(attribute.isBlank()){
                logInvalidEntry(errorString);
                return true;
            } else{
                return false;
            }
    }


    public void seedAccounts(){
        //by default, accountSeeding will take place with the accounts provided by the config file.
        
        File initialAccountsFile = new File("../config/initialAccounts.txt");
        Scanner scanner;

        try{
            scanner = new Scanner(initialAccountsFile);
        } catch (Exception FileNotFoundException) {
            logInvalidEntry("Exception while seeding accounts: config file not found.");
            errorLogIndex=0;
            errorLog = new String[100];
            return;
        }

        seedAccounts(scanner);
    }

    public void seedAccounts(Scanner scanner){

        //iterate through the scanner for account seeding. Scanner is passed as a parameter to make unit testing easier.
        int lineCounter = 1;

        String accountIdCache = "";
        //used to check if a unique account attribute has already been parsed
        boolean customerCreated = false;
        boolean courierCreated = false;
        boolean bakerCreated = false;
        boolean operatorCreated = false;
        while(scanner.hasNextLine()){
            String parsedLine = scanner.nextLine();

            if (parsedLine.isBlank()){
                logInvalidEntry(" ; Encountered blank entry at line "+lineCounter+" while seeding accounts."); 
                lineCounter++;
                continue;
            }

            String[] parsedVals = parsedLine.split(",",-1);
            if(parsedVals.length != 8){
                logInvalidEntry(" ; Encountered improper number of attributes at line "+lineCounter+" while seeding accounts.");
                //logs a blank line, as we cannot know where the password is and thus cannot remove it.
                lineCounter++;
                continue;
            }

            //parsed line minus the password, used when logging errors
            String errorString = parsedVals[0]+parsedVals[1]+parsedVals[3]+parsedVals[4]+parsedVals[5]+parsedVals[6]+parsedVals[7];

            //error checks for parsed fields
            String accountId = parsedVals[0];
            if(testLineValidity(accountId, lineCounter, errorString+" ; accountId missing")){
                lineCounter++;
                continue;
            }
            String username = parsedVals[1];
            if(testLineValidity(username, lineCounter, errorString+" ; username missing")){
                lineCounter++;
                continue;
            }
            String password = parsedVals[2];
            if(testLineValidity(password, lineCounter, errorString+" ; password missing")){
                lineCounter++;
                continue;
            }
            String displayName = parsedVals[3];
            if(testLineValidity(displayName, lineCounter, errorString+" ; displayName missing")){
                lineCounter++;
                continue;
            }
            String contactEmail = parsedVals[4];
            String deliveryAddress = parsedVals[5];
            Role role;
            String holdRole = parsedVals[6];
            if(testLineValidity(holdRole, lineCounter, errorString)){
                lineCounter++;
                continue;
            }
            switch(holdRole){
                case "CUSTOMER":
                    role = Role.CUSTOMER;
                    break;
                case "RESTAURANT":
                    role = Role.RESTAURANT;
                    break;
                case "COURIER":
                    role = Role.COURIER;
                    break;
                case "OPERATOR":
                    role = Role.OPERATOR;
                    break;
                default:
                    logInvalidEntry(errorString+" ; Encountered invalid account role at line "+lineCounter+" while seeding accounts.");
                    lineCounter++;
                    continue;
            }
            String bakeryId  = parsedVals[7];

            //check role-specific logic
            if (role == Role.RESTAURANT && bakeryId.isBlank()) {
                logInvalidEntry(errorString+" ; Encountered blank bakeryId for bakery at line "+lineCounter+" while seeding accounts.");
                    lineCounter++;
                    continue;
            }
            if (role != Role.RESTAURANT && !bakeryId.isBlank()) {
                logInvalidEntry(errorString + " ; Encountered bakeryId for non-bakery account at line "+lineCounter+" while seeding accounts.");
                    lineCounter++;
                    continue;
            }
            if (role != Role.CUSTOMER && !deliveryAddress.isBlank()) {
                logInvalidEntry(errorString+" ; Encountered deliveryAddress for non-customer account at line "+lineCounter+" while seeding accounts.");
                    lineCounter++;
                    continue;
            }
            if (role == Role.CUSTOMER && deliveryAddress.isBlank()) {
                logInvalidEntry(errorString+" ; Encountered customer account without deliveryAddress at line "+lineCounter+" while seeding accounts.");
                    lineCounter++;
                    continue;
            }

            //check for duplicated account info within the caches
            if(accountIdCache.contains(accountId)){
                logInvalidEntry(errorString+" ; Encountered duplicate accountId at line "+lineCounter+" while seeding accounts.");
                lineCounter++;
                continue;
            }

            switch(role){
                case CUSTOMER:
                    customerCreated=true;
                    break;
                case RESTAURANT:
                    bakerCreated=true;
                    break;
                case COURIER:
                    courierCreated=true;
                    break;
                case OPERATOR:
                    operatorCreated=true;
                    break;
            }

            try{
                BakewayAccount account = new BakewayAccount(
                    accountId,
                    username,
                    passwordEncoder.encode(password),
                    displayName,
                    contactEmail,
                    deliveryAddress,
                    role,
                    bakeryId);

                createdAccounts[createdAccountsIndex] = account;
                createdAccountsIndex++;
                //add accountId to cache for duplicate checks in the following iterations
                accountIdCache+=" ; "+accountId;
            } catch(Exception e){
                logInvalidEntry(errorString + " ; Creation exception thrown for account at line "+lineCounter+" while seeding accounts.");
            }
            lineCounter++;
        }

        if(customerCreated && bakerCreated && courierCreated && operatorCreated){
            accountRegistry.deleteAll();
            sessionManager.invalidateAllSessions();

            for(int i=0; i<createdAccountsIndex; i++){
                accountRegistry.save(createdAccounts[i]);
            }
            logger.info("Account seeding successful.\nTotal of "+errorLogIndex+" errors logged\nTotal of "+createdAccountsIndex+" accounts seeded.");
        } else {
            logger.info("Not all roles created while seeding: \n>customerCreated: "+customerCreated+"\n>bakerCreated: "+bakerCreated+"\n>courierCreated: "+courierCreated+"\n>operatorCreated: "+operatorCreated
                +"\nTotal of "+errorLogIndex+" errors logged\nTotal of "+createdAccountsIndex+" accounts created but not seeded.");
        }
        
        errorLog = new String[100];
        createdAccounts = new BakewayAccount[100];
        errorLogIndex=0;
        createdAccountsIndex=0;

        scanner.close();
    }
}
