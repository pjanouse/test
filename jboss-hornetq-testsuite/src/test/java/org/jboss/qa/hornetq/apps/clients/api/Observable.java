package org.jboss.qa.hornetq.apps.clients.api;

/**
 *
 * @author mnovak
 */
public interface Observable {
  public void addObserver(Observer obsrNewObserver);
  public void removeObserver(Observer obsrToRemove);
  public void notifyObservers();
}
