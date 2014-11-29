package net.cfoster.sparrow;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Util
{
  public static Logger createLogger(final Level LOG_LEVEL)
  {
    Logger log = Logger.getAnonymousLogger();
    log.setLevel(LOG_LEVEL);
    ConsoleHandler chandler = new ConsoleHandler();
    chandler.setLevel(LOG_LEVEL);
    log.addHandler(chandler);
    log.setUseParentHandlers(false);
    return log;
  }
}
