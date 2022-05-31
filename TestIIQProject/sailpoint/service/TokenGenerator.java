package sailpoint.service;

import java.util.Date;

import javax.inject.Inject;

import org.apache.commons.lang3.RandomStringUtils;

import sailpoint.object.VerificationToken;
import sailpoint.tools.DateService;

/**
 * Handles the logic of generating a {@link VerificationToken}
 * 
 * @author tapash.majumder
 *
 */
public class TokenGenerator {
    
    /**
     * We create an interface so that we can swap out the
     * implementataion later if we want to.
     * 
     * @author tapash.majumder
     *
     */
    public interface RandomGenerator {
        String generate(int length);
    }
    
    /**
     * StringUtils based default implementation.
     * 
     * @author tapash.majumder
     *
     */
    public static class StringUtilsRandomGenerator implements RandomGenerator {

        @Override
        public String generate(int length) {
            return RandomStringUtils.randomNumeric(length);
        }
    }
    
    private RandomGenerator randomGenerator;
    private DateService dateService;
    
    /**
     * By having the RandomGenerator and DateCalculator injected we can 
     * swap out different implementations.
     * So now for unit tests we can advance the current time etc.
     */
    @Inject
    public TokenGenerator(RandomGenerator randomGenerator, DateService dateService) {
        this.randomGenerator = randomGenerator;
        this.dateService = dateService;
    }
    
    /**
     * Will generate a random token using the {@link #randomGenerator}
     * @param tokenLength how long the token needs to be
     * @param durationMins how long the token will be valid -- in minutes
     * @return zee Token generated
     */
    public VerificationToken generateVerificationToken(int tokenLength, int durationMins) {
        VerificationToken token = new VerificationToken();

        token.setCreateDate(dateService.getCurrentDate());

        token.setExpireDate(new Date(dateService.getCurrentDate().getTime() + durationMins * 60* 1000));
        
        token.setTextCode(randomGenerator.generate(tokenLength));
        
        return token;
    }
}
