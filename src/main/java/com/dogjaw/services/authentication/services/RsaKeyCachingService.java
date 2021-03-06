package com.dogjaw.services.authentication.services;

import com.dogjaw.services.authentication.b2c.AzureRsaKeys;
import com.dogjaw.services.authentication.b2c.RsaKeyB2C;
import com.dogjaw.services.authentication.b2c.RsaKeyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.jwt.crypto.sign.RsaVerifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Keith Hoopes on 2/25/2016.
 */
@Service("rsaKeyCachingService")
public class RsaKeyCachingService {

    @Autowired
    private RsaKeyClient rsaKeyClient;

    private ObjectMapper mapper = new ObjectMapper();

    private Map<String, RsaVerifier> verifierCache = new HashMap<>();
    private Map<String, RsaKeyB2C> keyCache = new HashMap<>();

    /**
     * Reloads the RSA keys for the signin, signup, and edit-profile policies
     * @throws IOException
     */
    @Scheduled(cron = "${azure.policy.verifier-refresh-cron}")
    public synchronized void refreshRsaKeys() throws IOException {

        verifierCache.clear();
        keyCache.clear();
        loadSigninRsaKey();
        loadSignupRsaKey();
        loadEditProfileRsaKey();
    }

    /**
     * Retrieve the RsaVerifier for the given "kid" field of the token.
     *
     * @param kid JWT kid
     *
     * @return {@link RsaVerifier}
     */
    public RsaVerifier getRsaVerifier(String kid){
        assert kid != null;

        RsaVerifier rsaVerifier = verifierCache.get(kid);
        if(rsaVerifier == null){
            // Exception is throw because we expect this to possibly
            // happen at runtime due to expired keys or an attack.
            throw new IllegalArgumentException("Invalid \"kid\" value.");
        }
        return rsaVerifier;
    }

    /**
     * Retrieve the RsaKeyB2C for the given "kid" field of the token.
     *
     * @param kid JWT kid
     *
     * @return {@link RsaKeyB2C}
     */
    public RsaKeyB2C getRsaKeyB2C(String kid){
        assert kid != null;

        return keyCache.get(kid);
    }

    /**
     * Reloads the RSA key for the signin policy
     * @throws IOException
     */
    private void loadSigninRsaKey() throws IOException {

        setAzureRsaKeys(rsaKeyClient.getSigninRsaKey());
    }

    /**
     * Reloads the RSA key for the signup policy
     * @throws IOException
     */
    private void loadSignupRsaKey() throws IOException {

        setAzureRsaKeys(rsaKeyClient.getSignupRsaKey());
    }

    /**
     * Reloads the RSA key for the edit-profile policy
     * @throws IOException
     */
    private void loadEditProfileRsaKey() throws IOException {

        setAzureRsaKeys(rsaKeyClient.getEditProfileRsaKey());
    }



    /**
     * Populates the caches with the given keys.
     *
     * @param keys {@link byte[]}
     */
    private void setAzureRsaKeys(byte[] keys) throws IOException {
        assert keys != null;
        assert keys.length>0;

        AzureRsaKeys azureRsaKeys = mapper.readValue(new String(keys), AzureRsaKeys.class);
        List<RsaKeyB2C> b2cKeyList = azureRsaKeys.getKeys();

        for (RsaKeyB2C key : b2cKeyList) {

            String kid = key.getKid();
            BigInteger modulus = new BigInteger(key.getN().getBytes());
            BigInteger publicExponent = new BigInteger(key.getE().getBytes());
            RsaVerifier rsaVerifier = new RsaVerifier(modulus, publicExponent);

            verifierCache.put(kid, rsaVerifier);
            keyCache.put(kid, key);
        }
    }
}
