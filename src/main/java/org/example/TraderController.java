package org.example;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;


import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
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
    static String serverUrl = "https://api.upbit.com";
    static boolean successBuy = false;

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
                if (percentList.get(0) > 2 && percentList.get(1) > 1 && percentList.get(0) + percentList.get(1) > 3) {
                    return name;
                } else if (percentList.get(0) + percentList.get(1) > 3) {
//                    System.out.println(name);
//                    System.out.println("최근 2분동안 3프로 이상 상승");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException | org.apache.hc.core5.http.ParseException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "";
    }

    public static void buyOrder(String coinName, String expense) throws NoSuchAlgorithmException, UnsupportedEncodingException {

        HashMap<String, String> params = new HashMap<>();
        params.put("market", coinName);
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

//            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    static int count = 0;

    public static Double getMyAccountInfo(String coinName, String infoName) {
        coinName = coinName.replace("KRW-", "");

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
            if (entityString.equals("Too many API requests.")) {
                System.out.println("너무 많은 요청 오류!");
                return -1d;
            }

            JSONArray jsonObject = (JSONArray) jsonParser.parse(entityString);
            if (coinName.equals("KRW")) {
                JSONObject krwJson = (JSONObject) jsonObject.get(0);
                String krwStr = krwJson.get(infoName).toString();
                return Double.parseDouble(krwStr);
            } else {
                for (Object json : jsonObject) {
                    JSONObject coinJson = (JSONObject) json;
                    if (coinJson.get("currency").toString().equals(coinName)) {
                        String priceStr = coinJson.get(infoName).toString();
                        return Double.parseDouble(priceStr);
                    }
                }
            }

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static Double getOneCoinPrice(String coinName) {

        JSONParser jsonParser = new JSONParser();
        String serverUrl = "https://api.upbit.com";

        NumberFormat f = NumberFormat.getInstance();
        f.setGroupingUsed(false);

        try {
            int count = 1;
            CloseableHttpClient client = HttpClients.createDefault();
            HttpGet request = new HttpGet(serverUrl + "/v1/candles/minutes/1?market=" + coinName + "&count=" + count);

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
                    jsonOne = (JSONObject) jsonObject.get(i);
                    String price = f.format(jsonOne.get("trade_price"));
                    priceList[i] = Double.parseDouble(price);
                }
                return priceList[0];
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException | org.apache.hc.core5.http.ParseException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return -1d;
    }

    public static void sellOrder(String coinName, String volume) throws NoSuchAlgorithmException, UnsupportedEncodingException {

        HashMap<String, String> params = new HashMap<>();
        params.put("market", coinName);
        params.put("side", "ask");
        params.put("volume", volume);
        params.put("ord_type", "market");

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

//            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

//    public static void getApiKeys() throws IOException {
//        String protocol = "file:/";
//        String rootPath = System.getProperty("user.dir");
//        String propertiesPath = "/api.properties";
//
//        StringBuilder filePath = new StringBuilder(protocol)
//                .append(rootPath)
//                .append(propertiesPath);
//
//        URL propURL = new URL(filePath.toString());
//
//        Properties properties = new Properties();
//        properties.load(propURL.openStream());
//
//        accessKey = properties.getProperty("accessKey");
//        secretKey = properties.getProperty("secretKey");
//    }


    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException {
//        getApiKeys();
        String target = "";
        List<String> list = getNames();


        loop1:
        while (true) {
            Double buyPrice = 0d;
            Double volume = 0d;
            Double krw = 0d;
            System.out.println("");
            while (true) {// sellOrder을 날리고 바로 오는 것이 아닌, sellOrder가 처리되면 오도록 해야함.
                if (getMyAccountInfo("KRW", "balance") > 5000) {
                    krw = (getMyAccountInfo("KRW", "balance") / 100) * 99.8;
                    System.out.println("이번 자금:" + krw);
                    break;
                }else if(getMyAccountInfo("KRW","balance") == -1d){
                    Thread.sleep(500);
                }
            }
            System.out.println("급상승 코인 찾는중");

            loop2:
            while (true) {

                for (int i = 0; i < list.size(); ) {
                    String a = list.get(i);
//                    System.out.println(a);
                    if (a.equals("KRW-BTT")) {
                        i++;
                        continue;
                    }

                    target = getTargetNameByPercent(a);
                    if (!"".equals(target)) {
                        System.out.println("target: " + target);
                        break;
                    }

                    if (i == list.size() - 1) {
                        i = 0;
                    } else {
                        i++;
                    }
                }

                if (krw > 0 && !"".equals(target)) {
                    System.out.println("구매 금액:" + krw);
                    buyOrder(target, String.valueOf(krw));
                    krw = 0d;
                    break loop2;
                }
            }
            if(getMyAccountInfo("KRW","balance") == -1d){
                Thread.sleep(500);
            }
            while (getMyAccountInfo("KRW", "locked") > 100) {
                Thread.sleep(100);
            }
            while (buyPrice == 0 || volume == 0) {
                Thread.sleep(500);
                buyPrice = getMyAccountInfo(target, "avg_buy_price");
                volume = getMyAccountInfo(target, "balance");
            }

            System.out.println("구매가 :" + buyPrice);
            System.out.println("구매량 :" + volume);
            System.out.println("코인 매도 가격 감시 중");

            loop3:
            while (true) {
                if(getMyAccountInfo("KRW","balance") == -1d){
                    Thread.sleep(500);
                }
                Double nowPrice = getOneCoinPrice(target);
                if (nowPrice > buyPrice * 1.015) {
                    sellOrder(target, "" + volume);
                    System.out.println("이익 주문! +1");
                    break;
                } else if (nowPrice < buyPrice * 0.985) {
                    sellOrder(target, "" + volume);
                    System.out.println("손해 주문! -1");
                    break;
                }
            }
            if(getMyAccountInfo("KRW","balance") == -1d){
                Thread.sleep(1000);
            }
            krw = getMyAccountInfo("KRW", "locked");
            while (krw != 0) {
                krw = getMyAccountInfo("KRW", "locked");
                if(krw == -1d){
                    Thread.sleep(500);
                }
            }
        }
    }
}
