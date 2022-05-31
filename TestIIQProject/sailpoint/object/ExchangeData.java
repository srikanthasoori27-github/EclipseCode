package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.tools.Util;

public class ExchangeData {

    private static final String ATT_EXCHANGE_FOREST_NAME    = "exchangeForest";
    private static final String ATT_EXCHANGE_HOSTS          = "ExchHost";
    private static final String ATT_EXCHANGE_USER           = "user";
    private static final String ATT_EXCHANGE_PASSWORD       = "password";
    private static final String ATT_EXCHANGE_ACCOUNT_FOREST = "accountForestList";
    private static final String ATT_EXCHANGE_USE_TLS        = "useTLS";
    
    
    String         exchangeForest;
    List<String>   exchHost;
    String         user;
    String         password;
    List<String>   accountForestList;
    String         accountForest;
    boolean        useTLS;

    public ExchangeData() {
        exchangeForest    = null;
        user              = null;
        password          = null;
        exchHost          = new ArrayList<String>();
        accountForestList = new ArrayList<String>();
        accountForest     = null;
    }

    @SuppressWarnings("unchecked")
    public ExchangeData(Map data) {

        accountForestList = new ArrayList<String>();
        if(data.get(ATT_EXCHANGE_FOREST_NAME) != null) {
            exchangeForest = (String) data.get(ATT_EXCHANGE_FOREST_NAME);
        }
        if(data.get(ATT_EXCHANGE_USER) != null) {
            user = (String) data.get(ATT_EXCHANGE_USER);
        }
        if (data.get(ATT_EXCHANGE_PASSWORD) != null) {
            password = (String) data.get(ATT_EXCHANGE_PASSWORD);
        }
        if(data.get(ATT_EXCHANGE_HOSTS) != null) {
            exchHost = (List<String>) data.get(ATT_EXCHANGE_HOSTS);
        }
        if(data.get(ATT_EXCHANGE_ACCOUNT_FOREST) != null) {
                accountForestList = (List<String>) data.get(ATT_EXCHANGE_ACCOUNT_FOREST);
        }
        if(data.get(ATT_EXCHANGE_USE_TLS) != null) {
            useTLS = Util.otob(data.get(ATT_EXCHANGE_USE_TLS));
        }
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // returns single account forest name to show on UI
    public List<String> getAccountForestList() {
        List<String> tempList = new ArrayList<String>();
        if(!Util.isEmpty(accountForestList)) {
            for(String forestName : Util.safeIterable(accountForestList)) {
                if(Util.isNotNullOrEmpty(forestName)) {
                    tempList.add(forestName);
                }
            }
        }
        return tempList;
    }

    // returns all the list of account forest name to save in application
    public List <String> getAccountForests() {
        return accountForestList;
    }

    public void setAccountForestList(List userForestList) {
        accountForestList = userForestList;
    }

    public String getExchangeForest() {
        return exchangeForest;
    }

    public void setExchangeForest(String exchangeForest) {
        this.exchangeForest = exchangeForest;
    }

    public List<String> getExchHost() {
        List tempList = new ArrayList();
        if(!Util.isEmpty(exchHost)) {
            String tempServer = null;
            for(int i=0 ; i < exchHost.size(); i++) {
                tempServer = exchHost.get(i);
                if(Util.isNotNullOrEmpty(tempServer)) {
                    tempList.add(tempServer);
                }
            }
        }
        return tempList;
    }

    public void setExchHost(List exchHostList) {
        exchHost = exchHostList;
    }

    public boolean isUseTLS() {
        return useTLS;
    }

    public void setUseTLS(boolean useSSL) {
        this.useTLS = useSSL;
    }

}
