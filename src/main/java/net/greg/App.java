package net.greg;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.*;
import java.util.*;

import java.nio.file.*;
import java.nio.file.attribute.*;


/**
 * Watch a specified directory, its sub-directories,
 * and the files within it.
 *
 * Curate a dictionary of watchKeys mapped to
 * a directory to track mods to that directory
 *
 *  Map<WatchKey, Path>
 */
public final class App {

  private static final String jvmLaunchPoint =
    System.getProperty("user.dir");  // whatever


  private final Map<WatchKey, Path> keys = new HashMap();

  private WatchService watcher;


  /**
   * Canonical entrypoint for a standalone Java app.
   */
  public static void main(String[] args) {
    new App().processEvents();
  }


  /**
   * Canonical entry point for a standalone Java app.
   */
  public App() {

    System.err.println(
      "\n\nWatching: " + jvmLaunchPoint);

    try {

      watcher = FileSystems.getDefault().newWatchService();

      register(Paths.get(jvmLaunchPoint));
    }
    catch (IOException e) { e.printStackTrace(); }
  }


  /**
   * Register a directory w/ the WatchService, storing
   * the watchKey and the directory in a dictionary.
   *
 	 * Invalid the watchKey by:
 	 *
 	 * <ul>
	 *   <li> Close the WatchService                </li>
 	 *   <li> Invoke the watchKey.cancel() method   </li>
 	 *   <li> The WatchService becomes inaccessible </li>
 	 * </ul>
 	 *
 	 * When reusing a watchKey inside a loop, remember to
   * call the watchKey.reset() method, which toggles the
   * watchKey to a READY state.
   *
   * The Path interface extends the Watchable interface,
   * which exposes the register() method, which in turn.
   * returns a WatchKey instance.
   */
  private void registerAPath(Path path) {

    try {

      WatchKey watchKey =
        path.register(
          watcher,
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_DELETE,
          StandardWatchEventKinds.ENTRY_MODIFY);

 			keys.put(watchKey, path);
 		}
 		catch (IOException e) { e.printStackTrace(); }
  }


  /**
   * Register with the WatchService, the given Path,
   * its sub-directories, and any files in it.
 	 *
 	 * Calls this method recursively while walking down a directory
 	 * structure and calling this for each directory we encounter.
   *
   * @see #processEvents()
   */
  private void register(Path start) {

    try {

      Files.walkFileTree(
        start, new SimpleFileVisitor<Path>() {

          @Override
          public FileVisitResult preVisitDirectory (
              Path path, BasicFileAttributes any) {

            registerAPath(path);

            return FileVisitResult.CONTINUE;
          }
        }
      );
    }
    catch (Throwable e) { }
  }


  /**
   * Process Events for watchKeys that have
   * been enqueued to the WatchService.
   *
 	 * This method calls watchKey.pollEvents(),
   * which returns the collection of change
   * events in form of a stream.
 	 *
 	 * When reusing a watchKey in a loop
 	 * don’t forget to call watchKey.reset()
   * method to toggle it to the  READY state.
 	 *
 	 * NB
   * The underlying OS affects how Events are detected.
   * The OS *may* alter the observation of Events with
   * respect to their relative timeliness and ordering.
   *
   * The above factors may result in a certain change
   * to the file-system on one OS triggering a single
   * Event-entry (in the Event Map), while on another
   * OS, the identical file-system change may trigger
   * multiple Event-entries (in the Event Map).
   */
  private void processEvents() {

    for (;;) {

      WatchKey key;

      try {

        // wait for key to be signalled
        key = watcher.take();
      }
      catch (InterruptedException e) {
        return;
      }

      Path path = keys.get(key);

      if (null == path) {

        System.err.println(
          "WatchKey's Path - " + path +
          " - does not exist");

        continue;
      }

      // Please note that at any time a new directory is created, we will
      // register it with Watch Service and a new key will be added to map.
      for (WatchEvent<?> event : key.pollEvents()) {

        @SuppressWarnings("rawtypes")
        WatchEvent.Kind kind = event.kind();

        // Context for directory entry event is the file name of entry
        @SuppressWarnings("unchecked")
        Path name = ((WatchEvent<Path>)event).context();

        Path child = path.resolve(name);


        System.err.println(
          "\n" + event.kind().name() +
          "\n  " + child);

        // if directory is created, and watching recursively,
        // then register it and its sub-directories
        if (kind == ENTRY_CREATE) {

          if (Files.isDirectory(child)) {

            register(child);
          }
        }
      }

      /*
        Should the mapped-in directory -- (the Value, V) associated with
        the watchKey (key, K) in the Event Map -- become inaccessible,
        reset that watchKey, thereby removing it from said Event Map
       */
      boolean valid = key.reset();

      if ( ! valid) {

        keys.remove(key);

        // break out of the monitoring iteration
        // when all directories become inaccessible
        if (keys.isEmpty()) {
          break;
        }
      }
    }
  }
}
