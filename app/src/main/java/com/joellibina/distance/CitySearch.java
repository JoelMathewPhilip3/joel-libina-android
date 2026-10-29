package com.joellibina.distance;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

/** Worldwide manual city search; does not use the phone's location. */
final class CitySearch {
    static final class City {
        final String label;
        final double lat;
        final double lon;

        City(String label, double lat, double lon) {
            this.label = label;
            this.lat = lat;
            this.lon = lon;
        }

        @Override public String toString() { return label; }
    }

    static void open(Activity activity, int index, Runnable changed) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("City name, e.g. Dublin or Tokyo");

        new AlertDialog.Builder(activity)
            .setTitle("Search worldwide cities")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search", (dialog, which) -> {
                String query = input.getText().toString().trim();
                if (query.length() < 2) {
                    Toast.makeText(activity, "Enter at least 2 characters", Toast.LENGTH_LONG).show();
                    return;
                }

                new Thread(() -> {
                    ArrayList<City> found = new ArrayList<>();
                    String error = null;
                    HttpURLConnection connection = null;
                    try {
                        URL url = new URL("https://geocoding-api.open-meteo.com/v1/search?name="
                                + URLEncoder.encode(query, "UTF-8")
                                + "&count=15&language=en&format=json");
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setConnectTimeout(10000);
                        connection.setReadTimeout(10000);

                        try (InputStream stream = connection.getInputStream();
                             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                            byte[] chunk = new byte[4096];
                            int length;
                            while ((length = stream.read(chunk)) != -1) {
                                buffer.write(chunk, 0, length);
                            }
                            JSONObject result = new JSONObject(buffer.toString("UTF-8"));
                            JSONArray entries = result.optJSONArray("results");
                            if (entries != null) {
                                for (int i = 0; i < entries.length(); i++) {
                                    JSONObject city = entries.getJSONObject(i);
                                    String name = city.optString("name");
                                    String region = city.optString("admin1");
                                    String country = city.optString("country");
                                    String label = name + (region.isEmpty() ? "" : ", " + region)
                                            + (country.isEmpty() ? "" : ", " + country);
                                    found.add(new City(label,
                                            city.getDouble("latitude"),
                                            city.getDouble("longitude")));
                                }
                            }
                        }
                    } catch (Exception e) {
                        error = e.getMessage();
                    } finally {
                        if (connection != null) connection.disconnect();
                    }

                    final String failure = error;
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        if (failure != null) {
                            Toast.makeText(activity, "City search failed: " + failure,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (found.isEmpty()) {
                            Toast.makeText(activity, "No matching cities found. Try another spelling.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        String[] names = new String[found.size()];
                        for (int i = 0; i < found.size(); i++) names[i] = found.get(i).label;
                        new AlertDialog.Builder(activity)
                            .setTitle("Select city")
                            .setItems(names, (selectionDialog, position) -> {
                                City city = found.get(position);
                                String suffix = Integer.toString(index);
                                CoupleData.set(activity, "city" + suffix, city.label);
                                CoupleData.set(activity, "lat" + suffix, Double.toString(city.lat));
                                CoupleData.set(activity, "lon" + suffix, Double.toString(city.lon));
                                CoupleWidget.refresh(activity);
                                changed.run();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    });
                }).start();
            })
            .show();
    }
}
