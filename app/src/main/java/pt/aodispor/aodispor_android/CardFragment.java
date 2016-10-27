package pt.aodispor.aodispor_android;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.util.concurrent.TimeUnit;
import de.hdodenhof.circleimageview.CircleImageView;
import pt.aodispor.aodispor_android.API.ApiJSON;
import pt.aodispor.aodispor_android.API.HttpRequestTask;
import pt.aodispor.aodispor_android.API.Links;
import pt.aodispor.aodispor_android.API.OnHttpRequestCompleted;
import pt.aodispor.aodispor_android.API.Professional;
import pt.aodispor.aodispor_android.API.SearchQueryResult;

/**
 *  Class representing a card stack fragment.
 *  <p>
 *      This class controls all the behaviours of the card stack such as the discarding of a card.
 *      This class initializes the stack of cards in an array of RelativeLayout and iterates them.
 *  </p>
 */
public class CardFragment extends Fragment implements OnHttpRequestCompleted {

    /** used by preparePage and onHttpRequestCompleted to know if the request is to get the previous or next page or an enterily new query */
    private enum RequestType{prevSet,nextSet,newSet}
    /**used to know if the query was successful or not. <br><br>emptySet indicates that an answer was received but no results were found*/
    private enum QueryResult{timeout,emptySet,successful}

    private RequestType requestType;
    /**contains the previous page data
     * <br>rarely used (to avoid multiple requests), should be null most of the time*/
    private SearchQueryResult previousSet;
    /**contains the current page data*/
    private SearchQueryResult currentSet;
    /**contains the next page data
     * <br>set it to null if it does not have the next page data*/
    private SearchQueryResult nextSet;
    int currentSetCardIndex;
    private RelativeLayout[] cards;

    private RelativeLayout rootView;
    private LayoutInflater inflater;
    private ViewGroup container;

    /**
     * Default constructor for CardFragment class.
     */
    public CardFragment() {}

    /**
     * Factory method to create a new instance of CardFragment class. This is needed because of how
     * a ViewPager handles the creation of a Fragment.
     * @return the CardFragment object created.
     */
    public static CardFragment newInstance() {
        CardFragment fragment = new CardFragment();
        return fragment;
    }

    /**
     * This method creates the View of this card stack fragment.
     * @param i the LayoutInflater object to inflate card_zone.xml and card.xml.
     * @param c the root ViewGroup.
     * @param savedInstanceState object with saved states of previously created fragment.
     * @return returns the root view of the fragment. Not to be confused with the root ViewGroup of
     * this fragment.
     */
    @Override
    public View onCreateView(LayoutInflater i, ViewGroup c, Bundle savedInstanceState) {
        currentSetCardIndex=0;
        inflater = i;
        container = c;
        rootView = (RelativeLayout) i.inflate(R.layout.card_zone, container, false);

        cards = new RelativeLayout[3];
        switch (prepareNewSearchQuery()){
            case successful://received answer and it has professionals
                cards[0] = professionalCard(currentSet.data.get(0));
                if(currentSet.data.size()>1)
                {
                    cards[1] = professionalCard(currentSet.data.get(1));
                    if(currentSet.data.size()>2)   cards[2] = professionalCard(currentSet.data.get(2));
                    else                           cards[2] = createMessageCard("...","...");//TODO replace with xml defined strings
                }
                else {
                    cards[1] =  createMessageCard("...","...");//TODO replace with xml defined strings
                }

                SwipeListener listener = new SwipeListener(cards[0],((MainActivity)getActivity()).getViewPager(),this);
                cards[0].setOnTouchListener(listener);
                if(currentSet.data.size()>=2){
                    setCardMargin(2);
                    rootView.addView(cards[2]);
                }
                if(currentSet.data.size()>=1) {
                    setCardMargin(1);
                    rootView.addView(cards[1]);
                }
                break;
            case emptySet: //received answer but there aren't any professionals
                cards[0] = createMessageCard("...","...");//TODO replace with xml defined strings "NO PROFESSIONALS FOUND"
                break;
            case timeout: //did not receive answer
                cards[0] = createMessageCard("...","...");//TODO replace with xml defined strings "COULD NOT CONNECT"
                break;
            default: cards[0] = createMessageCard("...","...");//TODO replace with xml defined strings
                break;
        }
        rootView.addView(cards[0]);

        return rootView;
    }

    //region CARD POSITIONING UTILITIES

    /**
     * This method sets a card's margin from the stack so that it gives the illusion of seeing the
     * stack in perspective with the cards on top of each other.
     * @param position the position in the stack of a card.
     */
    public void setCardMargin(int position){
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(cards[position].getLayoutParams());
        params.addRule(RelativeLayout.ALIGN_LEFT,cards[position-1].getId());
        params.addRule(RelativeLayout.ALIGN_TOP,cards[position-1].getId());
        params.topMargin = dpToPx(5*position);
        params.leftMargin = dpToPx(5*position);
        cards[position].setLayoutParams(params);
    }

    /**
     *  This method centers the first card of the stack to the center of this fragment.
     */
    private void centerFirstCard(){
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(cards[1].getLayoutParams());
        params.addRule(RelativeLayout.CENTER_IN_PARENT,RelativeLayout.TRUE);
        params.addRule(RelativeLayout.ALIGN_LEFT,RelativeLayout.NO_ID);
        params.addRule(RelativeLayout.ALIGN_TOP,RelativeLayout.NO_ID);
        params.topMargin = 0;
        params.leftMargin = 0;
        cards[1].setLayoutParams(params);
    }

    /**
     * Auxiliary method to convert density independent pixels to actual pixels on the screen
     * depending on the systems metrics.
     * @param dp the number of density independent pixels.
     * @return the number of actual pixels on the screen.
     */
    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    //endregion

    //region NAVIGATION/PAGINATION

    /**
     * This method discards the top card of the card stack, destroys it and brings the other cards
     * one position further in the stack. After that creates a new card to be on the bottom of the
     * stack.
     * <br>
     *     Also responsible for requesting the loading of the next page and updating the currentSet and nextSet.
     */
    public void discardTopCard(){
        currentSetCardIndex++;
        centerFirstCard();
        rootView.removeAllViews();
        cards[0] = cards[1];
        cards[1] = cards[2];

        //TODO note sure if these next 2 ifs are working correctly, please verify !!!
        //before... had -> if cards[1/2].getId() == R.layout.message_card and did not work
        if(cards[1] == null) //already reached end of card pile
        {
            rootView.addView(cards[0]);
            return;
        }
        if(cards[2] != null && cards[2].getTag()!=null && cards[2].getTag().equals("msg")) //only one card left on pile card TODO not sure about this line, there may be a prettier way to to it
        {
            setCardMargin(1);
            rootView.addView(cards[1]);
            rootView.addView(cards[0]);
            SwipeListener listener = new SwipeListener(cards[0],((MainActivity)getActivity()).getViewPager(),this);
            cards[0].setOnTouchListener(listener);
            cards[2] = null;
            return;
        }
        if(currentSet==null) return;//TODO add exception here later

        //more than one card on card pile
        if(currentSetCardIndex +2<currentSet.data.size()) {
            cards[2] = professionalCard(currentSet.data.get(currentSetCardIndex + 2));
        }
        else
        {
            if(currentSet.meta.pagination.getLinks()!=null && currentSet.meta.pagination.getLinks().getNext()!=null)//if there are more pages to show
            {
                if(nextSet!=null) { //we already have the next page information
                    currentSetCardIndex = currentSetCardIndex-currentSet.data.size(); //negative when there are still cards from the previous set on the pile
                    Log.d("L155","currentSetCardIndex: " + Integer.toString(currentSetCardIndex));
                    currentSet = nextSet;
                    nextSet    = null;
                    cards[2]   = professionalCard(currentSet.data.get(currentSetCardIndex + 2));
                }
                else //content failed to get next page on time
                {
                    cards[2] = createMessageCard("...","...");//TODO display something when data not received HERE
                }
            }
            else //there are no more pages to show
            {
                cards[2] = createMessageCard("...","...");//TODO display some end of pile card HERE
            }
        }

        setCardMargin(1);
        setCardMargin(2);
        rootView.addView(cards[2]);
        rootView.addView(cards[1]);
        rootView.addView(cards[0]);
        SwipeListener listener = new SwipeListener(cards[0],((MainActivity)getActivity()).getViewPager(),this);
        cards[0].setOnTouchListener(listener);

        if(nextSet==null && currentSetCardIndex+AppDefinitions.MIN_NUMBER_OFCARDS_2LOAD>=currentSet.data.size())
        {
            prepareNextPage();
        }
    }

    /**
     * Recovers the previous discarded card
     * <also> reponsable for requesting the loading of the previous page and updating the currentSet and nextSet
     */
    public void restorePreviousCard() //TODO finish implementation and test restorePreviousCard
    {
        if(currentSetCardIndex<-2);//TODO not expected throw exception or development warning

        cards[1] = cards[0];
        cards[2] = cards[1];

        currentSetCardIndex--;
        if(currentSetCardIndex>=0){ //can get previous card from currentSet
            cards[0] = professionalCard(currentSet.data.get(currentSetCardIndex));
        }
        else if(currentSetCardIndex==-3) //transfer previousSet to currentSet if more than 2 cards already taken from it
        {
            currentSetCardIndex=previousSet.data.size()-3;
            nextSet = currentSet;
            currentSet = previousSet;
            previousSet = null;// no need to keep 3 sets stored
            cards[0] = professionalCard(currentSet.data.get(currentSetCardIndex));
        }else {
            if (currentSetCardIndex < 0) //needs to get card from previous set?
            {
                nextSet=null;//no need to keep 3 sets stored
                if(previousSet==null) //if previous set was not yet loaded
                {
                    switch (preparePreviousPage())
                    {
                        case successful:
                            break;
                        case emptySet: //received answer but there aren't any professionals
                            cards[0] = createMessageCard("...","...");//TODO replace with xml defined strings "NO PROFESSIONALS FOUND"
                            break;
                        case timeout: //did not receive answer
                            cards[0] = createMessageCard("...","...");//TODO replace with xml defined strings "COULD NOT CONNECT"
                            break;
                        default: cards[0] = createMessageCard("...","...");//TODO replace with xml defined strings
                            break;
                    }
                }
                if(previousSet!=null)//if set was received or was already loaded
                {
                    cards[0] = professionalCard(previousSet.data.get(
                            previousSet.data.size()+currentSetCardIndex));
                }
            }
        }

        rootView.removeAllViews();
        setCardMargin(1);
        setCardMargin(2);
        rootView.addView(cards[2]);
        rootView.addView(cards[1]);
        rootView.addView(cards[0]);
    }

    //endregion

    //region CARDS CREATION

    public RelativeLayout createProfessionalCard(String n, String p, String l, String d, String pr){
        RelativeLayout card = (RelativeLayout) inflater.inflate(R.layout.card, rootView, false);
        TextView name = (TextView) card.findViewById(R.id.title);
        name.setText(n);
        TextView profession = (TextView) card.findViewById(R.id.profession);
        profession.setText(p);
        TextView location = (TextView) card.findViewById(R.id.location);
        location.setText(l);
        TextView description = (TextView) card.findViewById(R.id.description);
        description.setText(d);
        TextView price = (TextView) card.findViewById(R.id.price);
        price.setText(pr);
        return card;
    }

    public RelativeLayout createMessageCard(String title, String message){
        RelativeLayout card = (RelativeLayout) inflater.inflate(R.layout.message_card, rootView, false);
        ((TextView) card.findViewById(R.id.title)).setText(title);
        ((TextView) card.findViewById(R.id.message)).setText(message);
        return card;
    }

    private RelativeLayout professionalCard(Professional p)
    {
        RelativeLayout card = createProfessionalCard(p.getFullName(),p.getTitle(),p.getLocation(),p.getDescription(),p.getRate());
        //TODO fetch professional image from web
        //((CircleImageView)card.findViewById(R.id.profile_image)).setImageDrawable(ContextCompat.getDrawable(getContext(),R.drawable.placeholder));
        return card;
    }

    //endregion

    //region API RELATED

    /**
     * send a new search query
     * <br>will wait for task to end or timeout (blocking)
     * @return true if received query result on time
     */
    public QueryResult prepareNewSearchQuery() //TODO replace test request with the actual request
    {
        requestType = RequestType.newSet;//not needed, unlike nextSet, should remain here anyways because it might be useful for debugging later
        HttpRequestTask request = new HttpRequestTask(SearchQueryResult.class,null
                ,"https://api.aodispor.pt/profiles/?query={query}&lat={lat}&lon={lon}"
                ,"tecnic","41","-8.1");//arqueologo (1), tecnic (91+9), desporto (10)

        SearchQueryResult result;
        try {
            result = (SearchQueryResult) request.execute().get(AppDefinitions.MILISECONDS_TO_TIMEOUT_ON_QUERY, TimeUnit.MILLISECONDS);
        }catch (Exception e)
        {
            Log.d("L290:EXCP",e.toString());
            return QueryResult.timeout;
        }
        if (result.data!=null && result.data.size()>0) {
            this.currentSet = result;
            return QueryResult.successful;
        }
        return QueryResult.emptySet;
    }

    /** will try to load next page on background via AsyncTask (nonblocking) */
    public void prepareNextPage()
    {
        requestType = RequestType.nextSet;
        Links links = currentSet.meta.pagination.getLinks();
        if(links==null) return;
        String link = links.getNext();
        if (link==null) return;
        if (links.getNext()!=null)
        {
            new HttpRequestTask(SearchQueryResult.class,this,link).execute();
        }
    }

    /** will wait for task to end or timeout (blocking) */
    public QueryResult preparePreviousPage() //TODO replace test request with the actual request
    {
        requestType = RequestType.prevSet;
        Links links = currentSet.meta.pagination.getLinks();
        if(links==null) return null;
        String link = links.getPrevious();
        if (link==null) return null;
        HttpRequestTask request = new HttpRequestTask(SearchQueryResult.class,null,link);

        SearchQueryResult result;
        try {
            result = (SearchQueryResult) request.execute().get(AppDefinitions.MILISECONDS_TO_TIMEOUT_ON_QUERY, TimeUnit.MILLISECONDS);
        }catch (Exception e)
        {
            Log.d("L330:EXCP",e.toString());
            return QueryResult.timeout;
        }
        if (result.data!=null && result.data.size()>0) {
            this.currentSet = result;
            return QueryResult.successful;
        }
        return QueryResult.emptySet;
    }

    @Override
    public void onHttpRequestCompleted(ApiJSON answer) {
        if(requestType==RequestType.nextSet)        nextSet     = (SearchQueryResult) answer;
        else if(requestType==RequestType.prevSet)   previousSet = (SearchQueryResult) answer;
        else if (requestType == RequestType.newSet){ //not used right now        {
            nextSet = null;
            currentSet = (SearchQueryResult) answer;
        }
    }

    //endregion

    //region MISC

    /**
     * This should never be accessed from the outside, except for testing purposes! (not your typical getter)
     * <br>was made this way to avoid implementing cloning
     * @return */
    public SearchQueryResult getCurrentSet()
    {
        return currentSet;
    }

    //endregion
}