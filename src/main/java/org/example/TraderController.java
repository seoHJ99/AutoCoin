package org.example;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;


import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.util.*;

public class TraderController {

    static String accessKey = "";
    static String secretKey = "";

    public static List<String> getNames() throws NoSuchAlgorithmException, UnsupportedEncodingException {

        JSONParser jsonParser = new JSONParser();
        String serverUrl = "https://api.upbit.com";
        List<String> result = new ArrayList<>();

        try {

            CloseableHttpClient client = HttpClients.createDefault();
            HttpGet request = new HttpGet(serverUrl + "/v1/market/all?isDetails=false");

            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            String entityString = (EntityUtils.toString(entity, "UTF-8"));
            JSONArray jsonObject = (JSONArray) jsonParser.parse(entityString);

            for (Object one : jsonObject) {
                JSONObject jsonOne = (JSONObject) one;
                String name = jsonOne.get("market").toString();
                if (name.contains("KRW")) {
                    result.add(name);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static String getTargetNameByPercent(String name) {

        JSONParser jsonParser = new JSONParser();
        String serverUrl = "https://api.upbit.com";

        NumberFormat f = NumberFormat.getInstance();
        f.setGroupingUsed(false);

        try {
            int count = 5;
            CloseableHttpClient client = HttpClients.createDefault();
            HttpGet request = new HttpGet(serverUrl + "/v1/candles/minutes/1?market=" + name + "&count=" + count);

            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            String entityString = (EntityUtils.toString(entity, "UTF-8"));
            if (entityString.equals("Too many API requests.")) {
                Thread.sleep(100);
            } else {
                JSONArray jsonObject = (JSONArray) jsonParser.parse(entityString);
                double[] priceList = new double[count];
                JSONObject jsonOne = null;
                for (int i = 0; i < count; i++) {
                    System.out.println(name);
                    jsonOne = (JSONObject) jsonObject.get(i);
                    String price = f.format(jsonOne.get("trade_price"));
                    priceList[i] = Double.parseDouble(price);
                }
                List<Double> percentList = new ArrayList<>();
                for (int i = 0; i < count - 1; i++) {
                    double diff = priceList[i] - priceList[i + 1];
                    double percent = Math.round(diff / priceList[i + 1] * 10000) / 100.0;
                    percentList.add(percent);
                }
                if (percentList.get(0) > 3) {
                    return name;
                } else if (percentList.get(0) + percentList.get(1) > 3) {
                    System.out.println("최근 2분동안 3프로 이상 상승");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new RuntimeException(e);
        }
        return "";
    }

    public static void order(String name, String expense) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        String serverUrl = "https://api.upbit.com";

        HashMap<String, String> params = new HashMap<>();
        params.put("market", name);
        params.put("side", "bid");
        params.put("price", expense);
        params.put("ord_type", "price");

        ArrayList<String> queryElements = new ArrayList<>();
        for (Map.Entry<String, String> entity : params.entrySet()) {
            queryElements.add(entity.getKey() + "=" + entity.getValue());
        }

        String queryString = String.join("&", queryElements.toArray(new String[0]));

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(queryString.getBytes("UTF-8"));

        String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .withClaim("query_hash", queryHash)
                .withClaim("query_hash_alg", "SHA512")
                .sign(algorithm);

        String authenticationToken = "Bearer " + jwtToken;

        try {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost request = new HttpPost(serverUrl + "/v1/orders");
            request.setHeader("Content-Type", "application/json");
            request.addHeader("Authorization", authenticationToken);
            request.setEntity(new StringEntity(new Gson().toJson(params)));

            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Double myAccount() {
        String serverUrl = "https://api.upbit.com";

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        String authenticationToken = "Bearer " + jwtToken;
        JSONParser jsonParser = new JSONParser();

        try {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpGet request = new HttpGet(serverUrl + "/v1/accounts");
            request.setHeader("Content-Type", "application/json");
            request.addHeader("Authorization", authenticationToken);

            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            String entityString = (EntityUtils.toString(entity, "UTF-8"));
            JSONArray jsonObject = (JSONArray) jsonParser.parse(entityString);
            JSONObject krwJson = (JSONObject) jsonObject.get(0);
            String krwStr = krwJson.get("balance").toString();
            return Double.parseDouble(krwStr);
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void getApiKeys() throws IOException {
        String protocol = "file:";
        String rootPath = System.getProperty("user.dir");
        String propertiesPath = "/api.properties";

        StringBuilder filePath = new StringBuilder(protocol)
                .append(rootPath)
                .append(propertiesPath);
        System.out.println(filePath);

        URL propURL = new URL(filePath.toString());

        Properties properties = new Properties();
        properties.load(propURL.openStream());

        accessKey = properties.getProperty("accessKey");
        secretKey = properties.getProperty("secretKey");
    }


    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        getApiKeys();
        Double krw = (myAccount()/100) * 99.8;
        String target = "";
        List<String> list = getNames();
        Double price = 0d;

        while (true) {
            for (String a : list) {
                System.out.println(a);
                if(a.equals("KRW-BTT")){
                    continue;
                }
                target = getTargetNameByPercent(a);
                if(!"".equals(target)){
                    break;
                }
            }
            if(price >0){
                order(target, String.valueOf(krw));
                break;
            }
        }
    }
}
