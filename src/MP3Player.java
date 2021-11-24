import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

/**
 * This class represents a text-based MP3 Player.
 */
public class MP3Player implements Runnable {

	// directory containing all the MP3 files
	public static final String SONGS_DIR = "songs";

	private List<Song> songs;
	private boolean isPlaying;
	private Thread thread;
	private Scanner scanner;
	private Player player;

	/**
	 * Initializes MP3 Player.
	 */
	public MP3Player() {
		songs = new ArrayList<>();
		isPlaying = false;
		thread = null;
		scanner = new Scanner(System.in);
		player = null;
		loadSongs();
	}

	/**
	 * Reads meta data from MP3 file and creates and returns Song object.
	 * 
	 * @param file MP3 file
	 * @return Song
	 */
	private Song createSong(File file) {
		try {
			// fetch meta data from MP3 file
			ContentHandler handler = new DefaultHandler();
			Metadata metadata = new Metadata();

			FileInputStream inputStream;
			inputStream = new FileInputStream(file);
			ParseContext pContext = new ParseContext();
			Mp3Parser mp3Parser = new Mp3Parser();
			mp3Parser.parse(inputStream, handler, metadata, pContext);

			// song title
			String title = metadata.get("title");
			if (title == null || title.trim().equals("") || title.trim().equals("null")) {
				title = "Unknown";
			}

			// song artist
			String artist = metadata.get("xmpDM:artist");
			if (artist == null || artist.trim().equals("")) {
				artist = "Unknown";
			}

			// song year
			String year = metadata.get("xmpDM:releaseDate");
			if (year == null || year.trim().equals("")) {
				year = "Unknown";
			} else {
				String[] parts = year.split("-"); // sometimes the field has date instead of year
				year = parts[0];
			}

			// song genre
			String genre = metadata.get("xmpDM:genre");
			if (genre == null || genre.trim().equals("")) {
				genre = "Unknown";
			}

			// song location
			String path = SONGS_DIR + "/" + file.getName();
			// by default we add songs in non favorite list as we dont have any tag in mp3
			// file
			return new Song(title, artist, genre, year, path, false);
		} catch (Exception e) {
			System.out.println("Error while reading MP3 metadata: " + e);
			return null;
		}
	}

	/**
	 * Opens the directory containing all the songs and populates internal database
	 * with songs.
	 */
	private void loadSongs() {
		File songsDir = new File(SONGS_DIR);

		// invalid path
		if (!songsDir.isDirectory()) {
			System.out.println("Invalid songs directory.");
			System.exit(0);
		}

		Song song;
		for (File file : songsDir.listFiles()) {
			boolean isMP3 =file.getName().toLowerCase().endsWith(".mp3");
			if (!isMP3) { // ignore the file if it is not MP3
				continue;
			}

			// create Song object and add into list
			song = createSong(file);
			if (song != null) {
				songs.add(song);
			}
		}

		// no songs found in directory
		// there is no need for the player to keep running, so exit the program
		if (songs.isEmpty()) {
			System.out.println("No songs found.");
			System.exit(0);
		}

		// show number of songs loaded
		System.out.println(songs.size() + " song(s) loaded.");
	}

	/**
	 * Displays the Songs from received list.
	 * 
	 * @param songs list of Songs
	 */
	private void showSongs(List<Song> songs) {
		for (int i = 0; i < songs.size(); i++) {
			System.out.println("[" + (i + 1) + "] " + songs.get(i));
		}
	}

	/**
	 * Asks user to enter the song number. If the number is not in range, it asks
	 * the user for the input again. User can cancel the operation by entering 0.
	 * 
	 * @param max upper bound
	 * @return user's choice
	 */
	private int askSongNumber(int max) {
		String input;
		int choice = -1;

		System.out.println();

		while (choice < 0 || choice > max) {
			System.out.print("Enter track number to play (0 to cancel): ");
			input = scanner.nextLine().trim();
			try {
				choice = Integer.parseInt(input);
				if (choice < 0 || choice > max) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException e) {
				System.out.println("Invalid track number. Try again.");
			}
		}

		return choice;
	}

	/**
	 * Sorts the list of songs by year in descending order and returns top count
	 * (latest) songs.
	 * 
	 * @param count number of songs to be returned
	 * @return list of latest songs
	 */
	private List<Song> getLatestSongs(int count) {
		if (songs.size() < count) {
			count = songs.size();
		}

		List<Song> songsSorted = new ArrayList<>(songs);

		// sort by year
		songsSorted.sort(new Comparator<Song>() {

			@Override
			public int compare(Song song1, Song song2) {
				if (song2.getYear().equalsIgnoreCase("unknown") || song1.getYear().equalsIgnoreCase("unknown")) {
					return 0;
				}
				return song2.getYear().compareTo(song1.getYear());
			}

		});

		// return slice of sorted songs list
		return songsSorted.subList(0, count);
	}

	/**
	 * Stops currently playing song.
	 */
	private void stop() {
		if (isPlaying) {
			player.close();
			thread.interrupt();
			player = null;
			thread = null;
			isPlaying = false;
		}
	}

	/**
	 * Plays the specified song.
	 * 
	 * @param song Song to play
	 */
	private void play(Song song) {
		stop();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			
			if (!song.isFavorite()) {
				System.out.println("\nIs this your favorite song\n1.Yes\n2.No\nEnter your choice");
				int n = Integer.parseInt(br.readLine());
				if (n == 1) {
					for(int i=0;i<songs.size();i++) {
						if(song==songs.get(i)) {
							songs.get(i).setFavorite(true);
						}
					}
				}
			}
			FileInputStream inputStream = new FileInputStream(new File(song.getPath()));
			player = new Player(inputStream);

			// play the song in new thread so that it does not block other operations
			thread = new Thread(this);
			thread.start();
			isPlaying = true;
			System.out.println("\nNow Playing: " + song);
			
			System.out.println("1.Pause\r\n" + 
					"2.Stop\r\n" + 
					"3.Rewind 5 seconds\r\n" + 
					"4.Forward 5 seconds\r\n" + 
					"5.Favorite\r\n" + 
					"Enter your choice");
			int num = Integer.parseInt(br.readLine());
			switch (num) {
			case 1:
				player.close();
				thread.interrupt();
				isPlaying=false;
				break;
			case 2:
				stop();
				break;
			case 3:
				
				break;
			case 4:
				stop();
				for(int i=0;i<songs.size();i++) {
					if(song==songs.get(i)) {
						if(i==(songs.size()-1)) {
							play(songs.get(0));
						}
						play(songs.get(i+1));
					}
				}
			break;
			case 5:
				song.setFavorite(true);
			break;

			default:
				break;
			}
			
			
			
		} 
		catch(IllegalMonitorStateException ex) {
			
		}
		catch (Exception e) {
			System.out.println("Error while trying to play song: " + e);
			stop();
		}
	}

	/**
	 * Shows the Home menu (5 latest songs) and asks the user to enter the song
	 * number to play.
	 */
	private void showHome() {
		System.out.println("\nHere are some latest songs");

		List<Song> latestSongs = getLatestSongs(5);
		showSongs(latestSongs);

		int songNo = askSongNumber(5);
		if (songNo != 0) {
			play(latestSongs.get(songNo - 1));
		}
	}

	/**
	 * Shows all the songs loaded by the program and asks the user to enter the song
	 * number to play.
	 */
	private void showLibrary() {
		System.out.println("\nShowing all the songs");
		showSongs(songs);

		int songNo = askSongNumber(songs.size());
		if (songNo != 0) {
			play(songs.get(songNo - 1));
		}
	}

	/**
	 * Finds and returns songs with the title matching with received query in the
	 * songs list.
	 * 
	 * @param title search query
	 * @return list of found songs
	 */
	private List<Song> search(String title) {
		title = title.toLowerCase();
		List<Song> matchingSongs = new ArrayList<>();
		for (Song song : songs) {
			if (song.getTitle().toLowerCase().contains(title)) {
				matchingSongs.add(song);
			}
		}
		return matchingSongs;
	}

	/**
	 * Asks the user to enter the criteria to search the songs by title. It shows
	 * the results and asks the user to enter the number of song to play.
	 * 
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	private void searchByTitle() throws NumberFormatException, IOException {
		System.out.print("\nEnter title to search (blank to cancel): ");
		String query = scanner.nextLine().trim();
		if (query.equals("")) { // user canceled searching
			return;
		}

		// search and show results
		List<Song> matchingSongs = search(query);
		System.out.println("\nFound " + matchingSongs.size() + " result(s)");
		if (matchingSongs.size() > 0) {
			showSongs(matchingSongs);
			int songNo = askSongNumber(matchingSongs.size());
			if (songNo != 0) {
				play(matchingSongs.get(songNo - 1));
			}
		}
	}

	/**
	 * Starts playing the song in a separate thread.
	 */
	@Override
	public void run() {
		if (player != null) {

			try {
				player.play();
			} catch (JavaLayerException e) {
				System.out.println("Error while trying to play the song: " + e);
			}
		}
	}

	/**
	 * Shows the main menu and manages the flow of the MP3 Player program.
	 * 
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	public void start() throws NumberFormatException, IOException {
		String choice = "H";

		while (!choice.equalsIgnoreCase("Q")) {
			System.out.println("\n====== MENU ======");
			System.out.println("[H]ome");
			System.out.println("[S]earch by title");
			System.out.println("[L]ibrary");
			System.out.println("[F]avorite");
			System.out.println("[Q]uit");
			System.out.print("Enter your choice: ");

			choice = scanner.nextLine().trim();

			if (choice.equalsIgnoreCase("H")) {
				showHome();
			} else if (choice.equalsIgnoreCase("S")) {
				searchByTitle();
			} else if (choice.equalsIgnoreCase("L")) {
				showLibrary();
			} else if (choice.equalsIgnoreCase("F")) {
				showFavorite();
			} else if (choice.equalsIgnoreCase("Q")) {
				stop();
				System.out.println("\nGood Bye!!!");
			} else {
				System.out.println("Invalid choice. Try again.");
			}
		}
	}

	public void showFavorite() {
		System.out.println("\nShows all favorite songs\n");
		int j = 0;
		List<Song> list = new ArrayList<>();
		for (int i = 0; i < songs.size(); i++) {
			if (songs.get(i).isFavorite()) {
				System.out.println((j + 1) + ". " + songs.get(i));
				j++;
				list.add(songs.get(i));
				
			} 
		}
		int songNo = askSongNumber(songs.size());
		if (songNo != 0) {
			play(list.get(songNo - 1));
		}
	}

	/**
	 * Works as an entry-point of the program.
	 * 
	 * @param args command line arguments
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	public static void main(String[] args) throws NumberFormatException, IOException {
		System.out.println("========== Welcome to MP3 Player ==========");
		MP3Player mp3Player = new MP3Player();
		mp3Player.start();
	}

}
