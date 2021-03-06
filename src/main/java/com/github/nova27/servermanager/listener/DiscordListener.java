package com.github.nova27.servermanager.listener;

import com.github.nova27.servermanager.ServerManager;
import com.github.nova27.servermanager.config.ConfigData;
import com.github.nova27.servermanager.config.Server;
import com.github.nova27.servermanager.utils.Bridge;
import com.github.nova27.servermanager.utils.Messages;
import com.github.nova27.servermanager.utils.minecraft.StandardEventListener;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.MessageActivity;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import sun.security.krb5.Config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.nova27.servermanager.listener.BungeeListener.Lobby;


public class DiscordListener extends ListenerAdapter {
    private ServerManager main;

    /**
     * コンストラクタ
     * @param main ServerManagerのオブジェクト
     */
    public DiscordListener(ServerManager main) {
        this.main = main;
    }

    /**
     * Botが起動完了したら
     */
    @Override
    public void onReady(ReadyEvent event) {
        //プレイ中のゲーム
        main.jda().getPresence().setGame(Game.playing(ConfigData.PlayingGame));

        main.log(Messages.ConnectedToDiscord_Log.toString());
        main.bridge.sendToDiscord(Bridge.Formatter(Messages.ConnectedToDiscord_Discord.toString(), ConfigData.ServerName));

        //ロビーサーバーの起動
        Lobby.Server_On();

        //タイマーの起動
        Lobby.StartTimer();
        main.log(Bridge.Formatter(Messages.TimerStarted_Log.toString(), ""+ConfigData.CloseTime, Lobby.Name));
        main.bridge.sendToDiscord(Bridge.Formatter(Messages.TimerStarted_Discord.toString(), ""+ConfigData.CloseTime, Lobby.Name));

        super.onReady(event);
    }

    /**
     * メッセージが送信されたら
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if(event.getAuthor().isBot()) return;
        if(event.getGuild() == null) return;
        if(event.getTextChannel().getIdLong() != ConfigData.ChannelId) {
            for(int i = 0; i < ConfigData.Server.length; i++) {
                if(ConfigData.Server[i].ConsoleChannelId == event.getTextChannel().getIdLong()){
                    //コンソールチャンネルIDと送信元が一致したら
                    ConfigData.Server[i].Exec_command(event.getMessage().getContentRaw(), "", null);
                }
            }

            return;
        }

        // コマンドかどうか
        String FirstString = ConfigData.FirstString;
        if (event.getMessage().getContentRaw().startsWith(FirstString)) {
            String command = event.getMessage().getContentRaw().replace(FirstString, "").split("\\s+")[0];
            String[] args = event.getMessage().getContentRaw().replaceAll(Pattern.quote(FirstString + command) + "\\s+", "").split("\\s+");

            if (command.equalsIgnoreCase("help")) {
                //helpコマンド
                main.bridge.embed(event.getAuthor().getName(), Messages.HelpCommand_desc.toString(), new String[][] {
                        {
                                FirstString + "help",
                                Messages.HelpCommand_help.toString()
                        },
                        {
                                FirstString + "info",
                                Messages.HelpCommand_info.toString()
                        },
                        {
                                FirstString + "status",
                                Messages.HelpCommand_status.toString()
                        },
                        {
                                FirstString + "enabled [ServerID] [true or false]",
                                Messages.HelpCommand_enabled.toString()
                        }
                });
            }else if (command.equalsIgnoreCase("info")) {
                //infoコマンド
                main.bridge.embed(event.getAuthor().getName(), Messages.InfoCommand_desc.toString(), new String[][] {
                        {
                                Messages.InfoCommand_IP.toString(),
                                ConfigData.IP
                        },
                        {
                                Messages.InfoCommand_Port.toString(),
                                ConfigData.Port
                        }
                });
            }else if (command.equalsIgnoreCase("status")) {
                main.bridge.sendToDiscord(Messages.Wait_Discord.toString());

                //statusコマンド
                String[][] Embed_text = new String[ConfigData.Server.length + 1][2];

                //BungeeCord統計情報
                Embed_text[0][0] = Messages.StatusCommand_bungee.toString();
                Embed_text[0][1] = Bridge.Formatter(Messages.StatusCommand_playercnt.toString(), ""+main.bridge.PlayerCount(0));

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //各サーバーの情報
                        for(int i = 0; i < ConfigData.Server.length; i++) {
                            Embed_text[i+1][0] = ConfigData.Server[i].Name;
                            Embed_text[i+1][1] =
                                    Bridge.Formatter(Messages.StatusCommand_id + "\n", ConfigData.Server[i].ID) +
                                    Bridge.Formatter(Messages.StatusCommand_enabled.toString() + "\n", ""+ConfigData.Server[i].Enabled) +
                                    Bridge.Formatter(Messages.StatusCommand_started.toString() + "\n", ""+ConfigData.Server[i].Started) +
                                    Bridge.Formatter(Messages.StatusCommand_switching.toString(), ""+ConfigData.Server[i].Switching);

                            if(ConfigData.Server[i].Enabled && ConfigData.Server[i].Started && !ConfigData.Server[i].Switching) {
                                //サーバーが起動していたら

                                //プレイヤー数の取得
                                final String[] result = {""};
                                ConfigData.Server[i].Exec_command("list", ConfigData.Server[i].getLogListCmd(), new StandardEventListener() {
                                    @Override
                                    public void exec(String result_tmp) {
                                        Matcher m = Pattern.compile("[0-9]+").matcher(result_tmp);
                                        if(m.find()) {
                                            result[0] = m.group();
                                        }
                                    }
                                });
                                while (result[0].equals("")) {
                                    //処理が終了するまで待機
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }

                                Embed_text[i+1][1] += Bridge.Formatter("\n"+Messages.StatusCommand_playercnt.toString(), ""+result[0]);
                            }
                        }

                        main.bridge.embed(event.getAuthor().getName(), Messages.StatusCommand_desc.toString(), Embed_text);
                    }
                });
                thread.start();
            }else if(command.equalsIgnoreCase("enabled")){
                //Adminかどうか
                if (!isAdmin(event.getGuild().getId())) {
                    main.bridge.sendToDiscord(Messages.EnabledCommand_permission.toString());
                    return;
                }

                //引数の数
                if(args.length < 2) {
                    main.bridge.sendToDiscord(Messages.EnabledCommand_syntaxerror.toString());
                    return;
                }

                boolean flag = Boolean.valueOf(args[1]);

                for(int i = 0; i <= ConfigData.Server.length - 1; i++) {
                    if(args[0].equals(ConfigData.Server[i].ID)) {
                        if(ConfigData.Server[i] == Lobby) {
                            main.bridge.sendToDiscord(Messages.EnabledCommand_tried_lobbyenabled_change.toString());
                            return;
                        }

                        if(ConfigData.Server[i].Enabled == flag) {
                            //変更がなければ
                            main.bridge.sendToDiscord(Bridge.Formatter(Messages.EnabledCommand_sameflag.toString(), ConfigData.Server[i].Name, ""+flag));
                            return;
                        }
                        if(flag) {
                            //trueなら
                            ConfigData.Server[i].Enabled = flag;
                            main.bridge.sendToDiscord(Bridge.Formatter(Messages.EnabledCommand_changedflag.toString(), ConfigData.Server[i].Name, ""+flag));
                        }else {
                            //falseなら
                            main.bridge.sendToDiscord(Messages.Wait_Discord.toString());

                            Server Target = ConfigData.Server[i];
                            Thread thread = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    if(Target.Started) {
                                        //開始していたら停止
                                        while(Target.Switching) {
                                            //処理完了まで待機
                                            try {
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        Target.Exec_command("stop", "", null);
                                        Target.Started = false;
                                        Target.Switching = true;
                                    }
                                    Target.Enabled = flag;

                                    main.bridge.sendToDiscord(Bridge.Formatter(Messages.EnabledCommand_changedflag.toString(), Target.Name, ""+flag));
                                }
                            });

                            thread.start();
                        }

                        return;
                    }
                }

                main.bridge.sendToDiscord(Messages.EnabledCommand_notfound.toString());
            }else {
                //無効なコマンドなら
                main.bridge.sendToDiscord(Messages.Command_notfound.toString());
            }
        }else {
            // コマンドでなければMinecraftへ送信
            main.bridge.sendToMinecraft(event.getMessage());
        }
    }

    /**
     * Adminかどうか確認する
     * @param ID 確認するユーザーID
     * @return Adminならtrue
     */
    public boolean isAdmin(String ID) {
        for(int i = 0; i < ConfigData.Admin_UserID.length; i++) {
            if(ID.equals(ConfigData.Admin_UserID[i])) {
                return true;
            }
        }
        return false;
    }
}
