package mybot;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.Messageable;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.server.invite.InviteBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.mariuszgromada.math.mxparser.Expression;
public class Main {
	static ClassLoader load = Thread.currentThread().getContextClassLoader();
	static BufferedReader Token = new BufferedReader(new InputStreamReader(load.getResourceAsStream("Token.txt")));
	static BufferedReader rin;
	static BufferedReader badwords = new BufferedReader(new InputStreamReader(load.getResourceAsStream("badwords.txt")));
	static BufferedReader sfwservers = new BufferedReader(new InputStreamReader(load.getResourceAsStream("badwords.txt")));
	static BufferedReader copy = new BufferedReader(new InputStreamReader(load.getResourceAsStream("sfw.txt")));
	static BufferedReader wlist = new BufferedReader(new InputStreamReader(load.getResourceAsStream("wlist.txt")));
	static BufferedReader deflist = new BufferedReader(new InputStreamReader(load.getResourceAsStream("deflist.txt")));
	static HashMap<Messageable, Thread> threads = new HashMap<Messageable, Thread>();
	static HashMap<Server, String> prefixes = new HashMap<Server, String>();
	static HashMap<String, String> dict = new HashMap<String, String>();
	static HashMap<Messageable, Integer> third = new HashMap<Messageable, Integer>();
	static StringBuilder allah = new StringBuilder();
	static StringBuilder space = new StringBuilder();
	static String message;
	static Messageable chan;
	static String pre;
	static String servername;
	static String[] pastas = new String[10];
	static MessageCreateEvent event;
	static HashSet<String> bad = new HashSet<String>();
	static HashSet<String> sfw = new HashSet<String>();
	static DiscordApi api;
	
	public static void main(String[] args) throws IOException {
		space.append((char)8203);
		for(int i = 0; i < 999; i++) space.append("\n");
		space.append((char)8203);//spaces
		
		for(int i = 0; i < 2000; i++) allah.append(((char)65021));//allah
		for(int i = 0; i < pastas.length; i++)pastas[i] = copy.readLine();
		for(int i = 0; i < 74; i++)bad.add(badwords.readLine());
		for(int i = 0; i < 2; i++)sfw.add(sfwservers.readLine());
		for(int i = 0; i < 117528; i++)dict.put(wlist.readLine(), deflist.readLine());
		
		api = new DiscordApiBuilder().setToken(Token.readLine()).login().join();
		System.out.println("Logged in!");
		
		String botname = Token.readLine();
		api.addMessageCreateListener(eve -> {
			event = eve;
			if(!event.getMessageAuthor().getDiscriminatedName().equals(botname)){
				if(!third.containsKey(event.getChannel())) {
					third.put(event.getChannel(), 0);
				}
				if(third.get(event.getChannel()) > 0) {
					event = eve;
					message = event.getMessageContent();
					chan = event.getChannel();
					pre = prefix(event.getServer().get());
					thirdmanager();
				}
				else {
					String check = event.getMessageContent().substring(0, prefix(event.getServer().get()).length()+1);
					if(check.equals(prefix(event.getServer().get())+"!")) {
						readmanager();
					}
					else if(event.getMessageContent().contains("diexit")) {//debug, exits program
						event.getChannel().sendMessage("Going Offline...");
						api.disconnect();
						System.exit(0);
					}
					else if(sfw.contains(event.getServer().get().getName())){
						for(String e:event.getMessageContent().toLowerCase().split(" ")) {
							if(bad.contains(e)) event.getChannel().sendMessage(bible());
						}
					}
				}
			}
		});
	}
	static void readmanager(){
		String[] cmd = event.getMessageContent().split(" ");
		chan = event.getChannel();
		pre = prefix(event.getServer().get());
		servername = event.getServer().get().getName();
		if(cmd.length>1) message = cmd[1].toLowerCase();
		switch(cmd[0].charAt(pre.length()+1)){
		case 'p':paste('p'); break;
		case 'm':paste('m'); break;
		case 'c':changeprefix(); break;
		case 's':stop(chan); break;
		case 'x':special(); break;
		}
	}
	static String prefix(Server us) {
		if(prefixes.containsKey(us)) return prefixes.get(us);
		return "sh";
	}
	static void paste(char pm){
		String pasted = "";
		switch(message) {
		case "insult":
			if(sfw.contains(servername)) {
				chan.sendMessage("This is a SFW Discord server, what you requested could be NSFW");
				return;
			}
			if(pm == 'm') {
				mt.chan = chan;
				mt.mode = 1;
				break;
			}
			pasted = insult(); break;
		case "allah" :
			pasted = allah.toString(); break;
		case "stop" :
			stop(chan); return;
		case "space" :
			pasted = space.toString(); break;
		case "plagueis" :
			pasted = pastas[0]; break;
		case "sorry" :
			pasted = pastas[1]; break;
		case "doctor" :
			pasted = pastas[2]; break;
		case "kira" :
			pasted = pastas[3]; break;
		case "pandemonika" :
			if(sfw.contains(servername)) {
				chan.sendMessage("This is a SFW Discord server, what you requested could be NSFW");
				return;
			}
			pasted = pastas[4]; break;
		case "navy":
			pasted = pastas[5]; break;
		case "fitness":
			pasted = pastas[6]; break;
		case "linux":
			pasted = pastas[7]; break;
		case "furry":
			if(sfw.contains(servername)) {
				chan.sendMessage("This is a SFW Discord server, what you requested could be NSFW");
				return;
			}
			pasted = pastas[8]; break;
		case "freeman":
			pasted = pastas[10]; break;
		case "pingme" :
			pasted = event.getMessageAuthor().asUser().get().getMentionTag(); break;
		case "cum" :
			if(sfw.contains(servername)) {
				chan.sendMessage("This is a SFW Discord server, what you requested could be NSFW");
				return;
			}
			if(pm == 'm') {
				mt.chan = chan;
				mt.mode = 2;
				break;
			}
			return;
		case "bruh":
			pasted = "bruh"; break;
		case "help":
			pasted = "https://github.com/rain1598/SpammersHaven"; break;
		case "mathhelp":
			pasted = "https://github.com/mariuszgromada/MathParser.org-mXparser#built-in-tokens"; break;
		case "invitebot":
			new MessageBuilder().setEmbed(new EmbedBuilder().setTitle("Bot Invite Link")
.setUrl("https://discord.com/api/oauth2/authorize?client_id=747632462191919204&permissions=3263489&redirect_uri=https%3A%2F%2Fdiscord.com%2Fapi%2Foauth2%2Fauthorize&scope=bot"))
			.send(chan); return;
		case "invite":
			pasted = new InviteBuilder(event.getServerTextChannel().get())
			.setMaxAgeInSeconds(0)
		    .setMaxUses(0)
		    .create().join().getUrl().toString(); break;
		case "nsfwtest":
			if(sfw.contains(servername)) {
				chan.sendMessage("This is a SFW Discord server, what you requested could be NSFW");
				return;
			}
			chan.sendMessage("NSFW");
		case "bee":
			if(pm == 'm') {
				mt.chan = chan;
				mt.mode = 3;
				break;
			}
		case "bible":
			if(pm == 'm') {
				mt.chan = chan;
				mt.mode = 4;
				break;
			}
			pasted = bible(); break;
		}
		if(pm == 'm') {
			mt.chan = chan;
			mt.spam = pasted;
			threads.put(chan, new mt());
			threads.get(chan).start();
		}
		else {
			chan.sendMessage(pasted);
		}
	}
	static String insult(){//Reddit insulter
		String re = "";
		int n = (int) (Math.random()*21795);
		try {
			rin = new BufferedReader(new InputStreamReader(load.getResourceAsStream("redditmoment.txt")));
			for(int i = 0; i < n; i++) rin.readLine();
			re = rin.readLine();
			if(re.length() > 2000)re = re.substring(0, 1999);
		} catch (IOException e) {}
		return re;
	}
	static String bible(){//bible verse generator
		String re = "";
		int n =(((int)(Math.random()*31102))*2);
		try {
			rin = new BufferedReader(new InputStreamReader(load.getResourceAsStream("bible.txt")));
			for(int i = 0; i < n; i++) rin.readLine();
			re = rin.readLine()+"\n"+rin.readLine();
		} catch (IOException e) {}
		return re;
	}
	static void stop(Messageable stchan) {//stops multithreading
		if(threads.containsKey(stchan)){
			threads.get(stchan).interrupt();
			threads.remove(stchan);
		}
	}
	static void changeprefix(){
		if(message.equals("sh")) {
			prefixes.remove(event.getServer().get());
			chan.sendMessage("prefix reset");
		}
		else {
			prefixes.put(event.getServer().get(), message);
			chan.sendMessage("prefix changed to: "+message);
		}
	}
	static void thirdmanager() {
		if(message.equals("diexit")){
			chan.sendMessage("Going Offline...");
			api.disconnect();
			System.exit(0);
		}
		if(message.equals("taskend")|message.equals(prefixes.get(event.getServer().get())+"!s")) {
			chan.sendMessage("Task Ended");
			third.put(chan, 0);
			return;
		}
		switch(third.get(event.getChannel())) {
		case 1:exp();break;
		case 2:dictionary();break;
		}
	}
	static void dictionary() {
		try {
			String[] defs = dict.get(message.toLowerCase()).split("#");
			for(int i = 0; i < defs.length; i++) chan.sendMessage(defs[i]);
		}catch(Exception e) {chan.sendMessage("Word not found");}
	}
	static void exp() {
		try {
			chan.sendMessage(Double.toString(new Expression(message).calculate()));
		}catch(Exception e) {chan.sendMessage("Syntax invalid");}
	}
	static void special() {
		switch(message) {
		case "math":
			new MessageBuilder().setEmbed(new EmbedBuilder()
					.setTitle("Math brought to you by mXparser, from MathParser.org")
					.setDescription("Read the documentation here")
					.setUrl("https://github.com/mariuszgromada/MathParser.org-mXparser#built-in-tokens")
			).send(chan);
			third.put(chan, 1); break;
		case "dict":
			new MessageBuilder().setEmbed(new EmbedBuilder()
					.setTitle("Dictionary Brought to you by OPTED")
					.setDescription("Please don't use obvious plurals\n(Cacti still works)")
			).send(chan);
			third.put(chan, 2); break;
		}
	}
}
class mt extends Thread{
	public static String spam;
	public static Messageable chan;
	public static int mode;
	public void run() {
		String sp = spam;
		Messageable ch = chan;
		try {
			switch(mode) {
			case 0:
				while(true) {
					ch.sendMessage(sp).join();
					TimeUnit.SECONDS.sleep(1);		
				}
			case 1:
				while(true) {
					ch.sendMessage(Main.insult()).join();
					TimeUnit.SECONDS.sleep(1);		
				}
			case 2:
				BufferedReader cum = new BufferedReader(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("cum.txt")));
				while(true) {
					String c2 = cum.readLine();
					if(c2 == null)cum = new BufferedReader(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("cum.txt")));
					ch.sendMessage(c2).join();
					TimeUnit.SECONDS.sleep(1);		
				}
			case 3:
				BufferedReader bee = new BufferedReader(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("bee.txt")));
				while(true) {
					String c2 = bee.readLine();
					if(c2 == null)return;
					ch.sendMessage(c2).join();
					TimeUnit.SECONDS.sleep(1);		
				}
			case 4:
				while(true) {
					ch.sendMessage(Main.bible()).join();
					TimeUnit.SECONDS.sleep(1);		
				}
			}
		} catch (InterruptedException e) {
			ch.sendMessage("Spam Stopped");
			return;
		} catch (IOException e) {}
	}
}