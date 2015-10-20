package com.st.nicobot.internal.bot;

import com.st.nicobot.bot.NicoBot;
import com.st.nicobot.bot.event.MessageEvent;
import com.st.nicobot.bot.utils.Emoji;
import com.st.nicobot.bot.utils.NicobotProperty;
import com.st.nicobot.bot.utils.Option;
import com.st.nicobot.services.BehaviorsService;
import com.st.nicobot.services.PropertiesService;
import com.ullink.slack.simpleslackapi.*;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Logs on 09-05-15.
 */
@Service
public class NicoBotImpl implements NicoBot {

    private static Logger logger = LoggerFactory.getLogger(NicoBotImpl.class);

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private PropertiesService props;

    @Autowired
    private BehaviorsService behaviors;

    //@Autowired
    //private APIMessageService api;

    private SlackSession session;

    private SlackPersona self;

    @PostConstruct
    private void postConstruct() {
        session = SlackSessionFactory.createWebSocketSlackSession(props.get(NicobotProperty.SLACK_API_KEY));
        Map<String, MessageEvent> eventMap = ctx.getBeansOfType(MessageEvent.class);

        session.addMessagePostedListener((message, session1) -> {
            logger.info("MSG [{}] ON CHAN {} BY {} : {}", message.getEventType(), message.getChannel().getName(), message.getSender().getUserName(), message.getMessageContent());
            //Message apiMessage = new Message(new DateTime(), message.getSender().getUserName(), message.getMessageContent());
            //api.saveMessages(Arrays.asList(apiMessage));
            behaviors.randomBehave(new Option(message));
        });

        for (Map.Entry<String, MessageEvent> entry : eventMap.entrySet()) {
            session.addMessagePostedListener(entry.getValue());
            logger.info("{} loaded", entry.getValue().getClass().getSimpleName());
        }

    }

    /**
     * Format une chaine de caractere en remplacant les "%p" par {@code sender} et les "%c" par {@code channel}.
     * @param message
     * @param sender
     * @param channel
     * @return
     */
    private String formatMessage(String message, SlackUser sender, SlackChannel channel) {
        if(sender != null) {
            message = message.replaceAll("%p", sender.getUserName());
        }
        message = message.replaceAll("%c", channel.getName());

        if(sender != null && message.contains("%u")) {
            message = message.replaceAll("%u", getRandomUserFromChannel(channel).getUserName());
        }

        return message;
    }

    private SlackUser getRandomUserFromChannel(SlackChannel channel) {
        int randomInt = RandomUtils.nextInt(0, channel.getMembers().size());
        return new ArrayList<>(channel.getMembers()).get(randomInt);
    }

    @Override
    public SlackMessageHandle sendMessage(SlackChannel channel, SlackUser origin, String message) {
        return session.sendMessage(channel, formatMessage(message, origin, channel), null);
    }

    @Override
    public SlackMessageHandle sendMessage(SlackChannel channel, SlackUser sender, String message, Emoji emoji) {
        SlackMessageHandle handle = sendMessage(channel, sender, message);

        session.addReactionToMessage(channel, handle.getSlackReply().getTimestamp(), emoji.getEmojiName());

        return handle;
    }

    @Override
    public SlackMessageHandle sendMessage(SlackMessagePosted originator, String message) {
        return sendMessage(originator.getChannel(), originator.getSender(), message);
    }

    @Override
    public SlackMessageHandle sendMessage(SlackMessagePosted originator, String message, Emoji emoji, boolean placeReactionOnBotMsg) {
        SlackMessageHandle handle = sendMessage(originator.getChannel(), originator.getSender(), message);
        String tstamp = originator.getTimeStamp();
        if(placeReactionOnBotMsg) {
            tstamp = handle.getSlackReply().getTimestamp();
        }
        session.addReactionToMessage(originator.getChannel(), tstamp, emoji.getEmojiName());
        return handle;
    }

    @Override
    public SlackMessageHandle sendPrivateMessage(SlackMessagePosted originator, String message) {
        for(SlackChannel channel : session.getChannels()) {
            if(channel.isDirect() && channel.getMembers().contains(originator.getSender())) {
                return session.sendMessage(channel, message, null);
            }
        }
        logger.warn("Direct channel with user {} not found !", originator.getSender());
        return null;
    }

    @Override
    public void connect() throws IOException {
        session.connect();
        self = session.sessionPersona();
    }

    @Override
    public boolean isSelfMessage(SlackMessagePosted message) {
        return message.getSender().getId().equals(self.getId());
    }

    @Override
    public String getBotName() {
        return self.getUserName();
    }

    @Override
    public Collection<SlackChannel> getChannels() {
        return session.getChannels().stream().filter(chan -> !chan.isDirect()).collect(Collectors.toList());
    }

    @Override
    public SlackChannel findChannelByName(String channelName) {
        return session.findChannelByName(channelName);
    }

    @Override
    public SlackUser findUserByUserName(String userName) {
        return session.findUserByUserName(userName);
    }

    @Override
    public SlackUser findUserById(String userId) {
        return session.findUserById(userId);
    }

    @Override
    public void addMessagePostedListener(SlackMessagePostedListener event) {
        session.addMessagePostedListener(event);
    }

    @Override
    public void removeMessagePostedListener(SlackMessagePostedListener event) {
        session.removeMessagePostedListener(event);
    }
}
