# Third-party notices

## Xray-core / AndroidLibXrayLite

TrueWeb uses AndroidLibXrayLite (`libv2ray.aar`) from 2dust, which wraps XTLS/Xray-core for Android.
The build workflow pins AndroidLibXrayLite release v26.7.31.

## GeoIP and GeoSite routing data

The release build downloads `geoip.dat` from v2fly/geoip and `geosite.dat` (`dlc.dat`) from v2fly/domain-list-community.
These files are used only for Smart Auto routing decisions such as `geoip:ru`, `geoip:private`, and `geosite:category-ru`.

Please review the upstream license terms in the respective repositories before distribution.
