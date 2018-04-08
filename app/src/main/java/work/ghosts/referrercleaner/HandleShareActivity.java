package work.ghosts.referrercleaner;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HandleShareActivity extends AppCompatActivity {
    private SQLiteDatabase db;
    private static final String TAG = "HandleShareActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handle_share);

        Intent intent = getIntent();
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        Log.d(TAG, "Incoming message: " + sharedText);

        List<String> links = matchLinks(sharedText);
        Log.d(TAG, "Links found: "+ links.toString());

        if (!links.isEmpty()){
            SQLiteDatabase db = initDatabaseConnection();

            Set<Pattern> cleanerPatterns = findApplicableGlobalRules(links);
            cleanerPatterns.addAll(findApplicableDomainRules(links));
            Log.d(TAG, "Applicable patterns: " + cleanerPatterns.toString());

            for (Pattern p : cleanerPatterns) {
                Matcher m = p.matcher(sharedText);
                try {
                    while (m.find()) {
                        sharedText = m.replaceAll("$1$2");
                        m = p.matcher(sharedText);
                    }
                }
                catch (Exception e) {
                    Log.e(TAG, "Pattern caused exception: " + p.toString());
                }
            }
        }

        Log.d(TAG, "Outgoing message: " + sharedText);
        shareText(sharedText);
        finish();
    }

    private SQLiteDatabase initDatabaseConnection() {
        MyDatabaseHelper dbHelper = new MyDatabaseHelper(this, "Rule.db", null, 1);
        db = dbHelper.getReadableDatabase();
        Cursor cur = db.rawQuery("SELECT COUNT(*) FROM Rule", null);
        if (cur != null && cur.moveToFirst())
            if (cur.getInt (0) == 0)
                initNewDatabase();

        return db;
    }

    private void initNewDatabase() {
        MyDatabaseHelper dbHelper = new MyDatabaseHelper(this, "Rule.db", null, 1);
        SQLiteDatabase WritableDB = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("domain", "music.163.com");
        values.put("rule", "(http://music.163.com/song/\\d+/)\\?userid=\\d+()");
        values.put("target", "$1");
        WritableDB.insert("Rule", null, values);
        values.clear();

        values.put("domain", "taobao.com");
        values.put("rule", "(https?:\\/\\/[\\w\\.]*taobao.com\\/.+\\?)spm=.+?(?:&(.+))?(?:\\s|$)");
        values.put("target", "$1$2");
        WritableDB.insert("Rule", null, values);

        WritableDB.close();
        Log.i(TAG, "initNewDatabase: New DB initialised");
    }


    private void shareText(String sharedText) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, sharedText);
        intent.setType("text/plain");
        startActivity(Intent.createChooser(intent, "Share"));
    }

    private Set<Pattern> findApplicableGlobalRules(List<String> links) {
        Set<Pattern> rules = new HashSet<Pattern>();
        rules.add(Pattern.compile("(https?:\\/\\/\\S+\\/\\S*\\?)utm_\\w+=[a-zA-z-_]+&?(.*?)")); // temp rule for testing
        rules.add(Pattern.compile("^(https?:\\/\\/\\S+\\/\\S*)\\?()$"));

        return rules;
    }

    private Set<Pattern> findApplicableDomainRules(List<String> links) {
        Set<Pattern> rules = new HashSet<Pattern>();
        for (String link: links){
            String domain;
            Matcher m = Pattern.compile("https?:\\/\\/([\\w.]+?)\\/").matcher(link);
            if (m.find()){
                domain = m.group(1);
                if (domain.indexOf("taobao.com") != -1)
                    domain = "taobao.com";
                Log.d(TAG, "findApplicableDomainRules: domain: " + domain);

                Cursor cursor = db.rawQuery(
                        "SELECT rule FROM Rule where domain = ?",
                        new String[] {domain});
                cursor.moveToFirst();
                String rule = cursor.getString(
                        (int) cursor.getColumnIndex("rule")
                );
//                String target = cursor.getString(
//                        cursor.getColumnIndex("target"));
                Log.d(TAG, "findApplicableDomainRules: rule: " + rule);

                rules.add(Pattern.compile(rule)); // temp rule for testing
            }
        }

        return rules;
    }

    private List<String> matchLinks(String sharedText) {
        List<String> links = new ArrayList<String>();
        Pattern linksPattern = Pattern.compile("https?:\\/\\/\\S+?\\/\\S*");
        Matcher m = linksPattern.matcher(sharedText);
        while (m.find()) {
            links.add(m.group());
        }
        return links;
    }
}
