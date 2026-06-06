<?php
/**
 * SavdoPRO — MXIK catalogue CORS relay.  HOST THIS ON AN UZBEK SERVER.
 *
 * The browser calls this over public HTTPS (e.g. https://yourdomain.uz/mxik.php?gtin=...),
 * so Chrome's "local network access" block never applies (unlike a localhost proxy).
 * This script — running in Uzbekistan — reaches tasnif.soliq.uz, then returns the
 * JSON with CORS headers so the browser can read it.
 *
 * Locked to the single MXIK endpoint; only forwards a sanitised numeric GTIN.
 * Requires PHP with the cURL extension (standard on virtually all hosting).
 *
 * DEPLOY: upload as e.g. mxik.php to any Uzbek PHP host with HTTPS.
 * TEST:   open https://yourdomain.uz/mxik.php?gtin=8907588001769  (expect JSON with НАСМОРК)
 * Then send me the full URL and I'll wire it into the app.
 */

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

header('Content-Type: application/json; charset=utf-8');

$gtin = preg_replace('/\D/', '', $_GET['gtin'] ?? '');
if ($gtin === '') {
    http_response_code(400);
    echo '{"error":"gtin required"}';
    exit;
}
$lang = preg_replace('/[^a-z]/i', '', $_GET['lang'] ?? 'uz');
if ($lang === '') {
    $lang = 'uz';
}

$url = 'https://tasnif.soliq.uz/api/cls-api/mxik/search/by-params'
     . '?size=1&page=0&lang=' . $lang . '&gtin=' . $gtin;

$ch = curl_init($url);
curl_setopt_array($ch, [
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_TIMEOUT        => 12,
    CURLOPT_FOLLOWLOCATION => true,
    CURLOPT_SSL_VERIFYPEER => true,
    CURLOPT_USERAGENT      =>
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      . '(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    CURLOPT_HTTPHEADER     => [
        'Accept: application/json',
        'Accept-Language: uz,ru;q=0.9,en;q=0.8',
        'Referer: https://tasnif.soliq.uz/',
    ],
]);
$body = curl_exec($ch);
$code = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
$err  = curl_error($ch);
curl_close($ch);

if ($body === false || $code === 0) {
    http_response_code(502);
    echo json_encode(['error' => 'upstream unreachable', 'detail' => $err]);
    exit;
}
http_response_code($code ?: 502);
echo $body;
