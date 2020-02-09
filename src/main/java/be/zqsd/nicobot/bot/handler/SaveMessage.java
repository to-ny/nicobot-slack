package be.zqsd.nicobot.bot.handler;

import be.zqsd.nicobot.bot.NicoBot;
import be.zqsd.nicobot.services.PersistenceService;
import be.zqsd.nicobot.services.PropertiesService;
import be.zqsd.nicobot.utils.NicobotProperty;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Julien
 */
@Service
public class SaveMessage extends AbstractMessageEvent {

    private static Logger logger = LoggerFactory.getLogger(SaveMessage.class);

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private NicoBot nicoBot;

    @Autowired
    private PropertiesService properties;

    private Pattern nameIdPattern = Pattern.compile("<@([0-9A-Z]+)>");
    private Pattern nameIdWithStrPattern = Pattern.compile("<@([0-9A-Z]+)\\|(.*?)>");
    private Pattern channelIdPattern = Pattern.compile("<#([0-9A-Z]+)>");

    @Override
    public void onEvent(SlackMessagePosted message, SlackSession session) {
        if (message.getChannel().getName().equals(properties.get(NicobotProperty.FEATURED_CHANNEL))) {
            onMessage(message);
        }
    }

    @Override
    public void onMessage(final SlackMessagePosted message) {
        Thread t = new Thread(() -> {
            try {
                persistenceService.saveMessage(message.getSender().getUserName(), replaceTokens(message.getMessageContent()));
            } catch (Exception e) {
                logger.error("Unable to save message", e);
            }
        });

        t.start();
    }

    private String replaceTokens(String message) {
        String returnMessage = message;
        Matcher nameIdMatcher = nameIdPattern.matcher(message);
        while (nameIdMatcher.find()) {
            String userID = nameIdMatcher.group(1);
            if (nicoBot.getSession().findUserById(userID) != null) {
                String username = nicoBot.getSession().findUserById(userID).getUserName();
                returnMessage = returnMessage.replaceAll("<@" + userID + ">", username);
            }
        }

        Matcher nameIdWithStrMatcher = nameIdWithStrPattern.matcher(message);
        while (nameIdWithStrMatcher.find()) {
            String userID = nameIdWithStrMatcher.group(1);
            String username = nameIdWithStrMatcher.group(2);
            returnMessage = returnMessage.replaceAll("<@" + userID + "\\|" + username + ">", username);
        }

        Matcher channelIdMatcher = channelIdPattern.matcher(message);
        while (channelIdMatcher.find()) {
            String chanID = channelIdMatcher.group(1);
            if (nicoBot.getSession().findChannelById(chanID) != null) {
                String username = nicoBot.getSession().findChannelById(chanID).getName();
                returnMessage = returnMessage.replaceAll("<#" + chanID + ">", username);
            }
        }
        return returnMessage;
    }

    /**
     * TODO:
     * Remplacer <@BBBB> -> username <@([0-9A-Z]+)>
     * Rempalcer <@BBB|xxx> -> xxx <@([0-9A-Z]+)\|(.*)>
     * Remplacer <#BBB> -> nom du chan <#([0-9A-Z]+)>
     */

}
