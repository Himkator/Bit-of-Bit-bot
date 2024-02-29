package com.example.channelbot.service;

import com.example.channelbot.config.Config;
import com.example.channelbot.model.*;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMembersCount;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;

@Service
public class Bot extends TelegramLongPollingBot {
    @Autowired
    private Config config;
    @Autowired
    private GameRepository gameRepository;
    private Map<Long, BotState> userStates = new HashMap<>();
    private String game_name="";
    private String genre="";
    private String desc="";
    private String link="";
    private byte[] bytes;
    private ArrayList<String> callbacks=new ArrayList<>();
    private ArrayList<String> All_callbacks=new ArrayList<>();
    private List<Game> games=new ArrayList<>();
    Game Del_game=new Game();
    public Bot(Config config) {
        this.config = config;
        //Добавление меню в чате сперва листом вклюсил все BotCommand это тип данных для команд
        List<BotCommand> listofCommands=new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "get a welcome message"));
        listofCommands.add(new BotCommand("/add", "add game"));
        listofCommands.add(new BotCommand("/delete", "delete one of games"));
        listofCommands.add(new BotCommand("/all", "all your games"));
        listofCommands.add(new BotCommand("/help", "info how to use bot"));
        try{
            //Меняет меню, чтобы туда попали вещи из списка
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            System.out.println("We have problem "+e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }


    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        String channelUsername = "@test_progra";
        //if(checkSubscription(update.getMessage().getChatId(), channelUsername)){
            if(update.hasMessage() && update.getMessage().hasText()){
                String msgText=update.getMessage().getText();
                long chatId=update.getMessage().getChatId();
                if(msgText.equals("/start"))
                    startCommand(chatId, update.getMessage().getChat().getFirstName());
                else if (msgText.equals("/add")) {
                    add(chatId);
                } else if (msgText.equals("/delete")) {
                    delete_game(chatId);
                } else if (msgText.equals("/all")) {
                    all_game(chatId);
                } else{
                    try {
                        //проверка состояние сообщение
                        processUserInput(chatId, msgText);
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }

                }
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                Message message = update.getMessage();
                PhotoSize photo = message.getPhoto().stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .orElse(null);

                if (photo != null) {
                    // Получение fileId фото
                    String fileId = photo.getFileId();

                    // Получение фотографии по fileId
                    GetFile getFile = new GetFile();
                    getFile.setFileId(fileId);

                    try{
                        File file=execute(getFile);
                        InputStream inputStream=new URL("https://api.telegram.org/file/bot" + getBotToken() + "/" + file.getFilePath()).openStream();
                        byte[] photoBytes = IOUtils.toByteArray(inputStream);
                        bytes=photoBytes;
                        try {
                            processUserInput(update.getMessage().getChatId(), "Ok");
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }catch (IOException | TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
            } else if (update.hasCallbackQuery()) {
                //берем его колбакдата для проверки
                String callBack=update.getCallbackQuery().getData();
                //получаем айди сообщение
                long messageId=update.getCallbackQuery().getMessage().getMessageId();
                //получение айди ползователя
                long chatId=update.getCallbackQuery().getMessage().getChatId();
                for (int i = 0; i < callbacks.size(); i++) {
                    if(callBack.equals(callbacks.get(i))){
                        String text="Вы уверены удалить игру "+games.get(i).getGame_name()+"?";
                        Del_game=games.get(i);
                        var Yes=new InlineKeyboardButton();
                        Yes.setText("Да");
                        Yes.setCallbackData("Yes");

                        var No=new InlineKeyboardButton();
                        No.setText("Нет");
                        No.setCallbackData("No");
                        deleteGame(chatId, messageId,text, Yes, No);
                    }
                }
                if(callBack.equals("Yes")){
                    var ga=gameRepository.findAll();
                    for(Game g:ga){
                        if(g.getGame_name().equals(Del_game.getGame_name())){
                            gameRepository.delete(g);
                        }
                    }
                    EditMessageText messageText=new EditMessageText();
                    messageText.setChatId(String.valueOf(chatId));
                    messageText.setText("Ok");
                    messageText.setMessageId((int) messageId);
                    try {
                        execute(messageText);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                } else if (callBack.equals("No")) {
                    sendMessage(chatId,"Ok");
                } else if (callBack.equals("Exit")) {
                    EditMessageText messageText=new EditMessageText();
                    messageText.setChatId(String.valueOf(chatId));
                    messageText.setText("Ok");
                    messageText.setMessageId((int) messageId);
                }
                for (int i = 0; i < All_callbacks.size(); i++) {
                    if(callBack.equals(All_callbacks.get(i))){
                        String text="Name: "+games.get(i).getGame_name()+"\n";
                        text+="Genre: "+games.get(i).getGenre()+"\n";
                        text+="Description: "+games.get(i).getDescription()+"\n";
                        text+="Link: "+games.get(i).getLink()+"\n";
                        sendMessage(chatId, text);

                        ByteArrayInputStream inputStream = new ByteArrayInputStream(games.get(i).getPhoto());
                        InputFile photo = new InputFile(inputStream, "photo.jpg");
                        SendPhoto sendPhoto = new SendPhoto();
                        sendPhoto.setChatId(String.valueOf(chatId));
                        sendPhoto.setPhoto(new InputFile(inputStream, "photo.jpg"));

                        try {
                            execute(sendPhoto);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        //}
    }

    private boolean checkSubscription(long chatId, String channelUsername) {
        GetChatMembersCount getChatMembersCount = new GetChatMembersCount();
        getChatMembersCount.setChatId(channelUsername);
        ChatMember chatMember = getChatMember(channelUsername, chatId);
        if (chatMember != null)
            return chatMember.getStatus().equals("member") || chatMember.getStatus().equals("administrator");
        return false;
    }

    private ChatMember getChatMember(String channelUsername, long chatId) {
        GetChatMember getChatMember = new GetChatMember();
        getChatMember.setChatId(channelUsername);
        getChatMember.setUserId(chatId);

        try {
            return execute(getChatMember);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return null;
        }
    }



    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void startCommand(long ChatId, String name){
        String text="Hi, "+name+", Add game";
        sendMessage(ChatId, text);
    }

    private void processUserInput(long chatId, String messageText) throws ParseException {
        BotState currentState = getUserState(chatId);

        switch (currentState) {
            case Game_name:
                game_name=messageText;
                sendMessage(chatId, "Жанр?");
                setUserState(chatId, BotState.Genre);
                break;
            case Genre:
                genre=messageText;
                sendMessage(chatId, "Описание?");
                setUserState(chatId, BotState.Desc);
                break;
            case Desc:
                desc=messageText;
                sendMessage(chatId, "Ссылка?");
                setUserState(chatId, BotState.Link);
                break;
            case Link:
                link=messageText;
                sendMessage(chatId, "Фото?");
                setUserState(chatId, BotState.Photo);
                break;
            case Photo:
                sendMessage(chatId, "Ok");

                addGame();
                break;
            default:
                String Idk="I dont know this function";
                sendMessage(chatId, Idk);
                break;
        }
    }
    //меняем состояние сообщение
    private void setUserState(long chatId, BotState state) {
        userStates.put(chatId, state);
    }
    //получение состояние сообщение
    private BotState getUserState(long chatId) {
        return userStates.getOrDefault(chatId, BotState.DEFAULT);
    }
    //удаление соостояние
    private void resetUserState(long chatId) {
        userStates.remove(chatId);
    }

    private void add(long chatId){
        sendMessage(chatId, "Имя");
        setUserState(chatId, BotState.Game_name);
    }

    private void addGame(){
        Game game=new Game();
        game.setGame_name(game_name);
        game.setGenre(genre);
        game.setDescription(desc);
        game.setLink(link);
        game.setPhoto(bytes);
        gameRepository.save(game);
    }

    private void delete_game(long chatId){
        String text="Какую удалить?\n";
        games=gameRepository.findAll();
        int i=1;
        for(Game game:games){
            text+=i+"."+game.getGame_name()+"\n";
            i+=1;
        }

        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        for (int j = 0; j < games.size(); j++) {
            List<InlineKeyboardButton> rowInline=new ArrayList<>();
            var game_name=new InlineKeyboardButton();
            game_name.setText((j+1)+"."+games.get(j).getGame_name());
            game_name.setCallbackData("Game_"+(j+1));
            callbacks.add("Game_"+(j+1));
            rowInline.add(game_name);
            rowsInline.add(rowInline);
        }
        if(games.isEmpty()) sendMessage(chatId, "Игр нет");
        else{
            var exit=new InlineKeyboardButton();
            exit.setText("Ок");
            exit.setCallbackData("Exit");
            List<InlineKeyboardButton> rowInline=new ArrayList<>();
            rowInline.add(exit);
            rowsInline.add(rowInline);
            SendMessage sendMsg=new SendMessage();
            sendMsg.setChatId(String.valueOf(chatId));
            sendMsg.setText(text);
            inKey.setKeyboard(rowsInline);
            sendMsg.setReplyMarkup(inKey);
            try{
                execute(sendMsg);
            }catch(TelegramApiException e){
                System.out.println("We have problem "+e.getMessage());
            }
        }
    }
    private void deleteGame(long chatId, long messageId, String text, InlineKeyboardButton Yes, InlineKeyboardButton No){
        EditMessageText messageText=new EditMessageText();
        messageText.setChatId(String.valueOf(chatId));
        messageText.setText(text);
        messageText.setMessageId((int) messageId);

        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        List<InlineKeyboardButton> rowInline=new ArrayList<>();
        rowInline.add(Yes);
        rowInline.add(No);
        rowsInline.add(rowInline);
        inKey.setKeyboard(rowsInline);
        messageText.setReplyMarkup(inKey);
        try{
            execute(messageText);
        }catch(TelegramApiException e){
            System.out.println("We have problem "+e.getMessage());
        }
    }

    private void all_game(long chatId){
        String text="Все игры:\n";
        games=gameRepository.findAll();
        int i=1;
        for(Game game:games){
            text+=i+"."+game.getGame_name()+"\n";
            i+=1;
        }

        InlineKeyboardMarkup inKey=new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline=new ArrayList<>();
        for (int j = 0; j < games.size(); j++) {
            List<InlineKeyboardButton> rowInline=new ArrayList<>();
            var game_name=new InlineKeyboardButton();
            game_name.setText((j+1)+"."+games.get(j).getGame_name());
            game_name.setCallbackData("All_Game_"+(j+1));
            All_callbacks.add("All_Game_"+(j+1));
            rowInline.add(game_name);
            rowsInline.add(rowInline);
        }
        if(games.isEmpty()) sendMessage(chatId, "Игр нет");
        else{
            var exit=new InlineKeyboardButton();
            exit.setText("Ок");
            exit.setCallbackData("Exit");
            text+="Чтобы узнать по подробнее нажми на название";
            List<InlineKeyboardButton> rowInline=new ArrayList<>();
            rowInline.add(exit);
            rowsInline.add(rowInline);
            SendMessage sendMsg=new SendMessage();
            sendMsg.setChatId(String.valueOf(chatId));
            sendMsg.setText(text);
            inKey.setKeyboard(rowsInline);
            sendMsg.setReplyMarkup(inKey);
            try{
                execute(sendMsg);
            }catch(TelegramApiException e){
                System.out.println("We have problem "+e.getMessage());
            }
        }
    }
}
