package org.maplibre.android.testapp.activity.style

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.testapp.R
import org.maplibre.android.testapp.styles.TestStyles
import timber.log.Timber
import java.net.URI
import java.net.URISyntaxException
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.CannotAddLayerException
import kotlin.random.Random
import java.util.Locale

/**
 * Test activity showcasing the heatmap layer api.
 */
class HeatmapLayerActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var maplibreMap: MapLibreMap
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heatmaplayer)
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(
            OnMapReadyCallback { map: MapLibreMap? ->
                if (map != null) {
                    maplibreMap = map
                }
                try {
                    maplibreMap.setStyle(
                        Style.Builder()
                            .fromUri(TestStyles.getPredefinedStyleWithFallback("Pastel"))
                    ) { style ->
                        // Add source and layers after style load; avoid referencing non-existent IDs
                        style.addSource(createEarthquakeSource())
                        style.addLayer(createHeatmapLayer())
                        try {
                            style.addLayerAbove(createCircleLayer(), HEATMAP_LAYER_ID)
                        } catch (e: CannotAddLayerException) {
                            // Fallback if anchor is missing
                            style.addLayer(createCircleLayer())
                        }

                        // Add a second, custom heatmap with clearly different styling at a known location
                        style.addSource(createTestHeatSource())
                        style.addLayerAbove(createCustomHeatmapLayer(), HEATMAP_LAYER_ID)

                        // Center camera over the custom heat area (Sulaimaniyah, Kurdistan Region, Iraq)
                        maplibreMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(CUSTOM_HEAT_LAT, CUSTOM_HEAT_LON), 13.0)
                        )
                    }
                } catch (exception: URISyntaxException) {
                    Timber.e(exception)
                }
            }
        )
    }
    // # --8<-- [start:createEarthquakeSource]
    private fun createEarthquakeSource(): GeoJsonSource {
        return GeoJsonSource(EARTHQUAKE_SOURCE_ID, URI(EARTHQUAKE_SOURCE_URL))
    }
    // # --8<-- [end:createEarthquakeSource]

    private fun createHeatmapLayer(): HeatmapLayer {
        val layer = HeatmapLayer(HEATMAP_LAYER_ID, EARTHQUAKE_SOURCE_ID)
        layer.maxZoom = 9f
        layer.setSourceLayer(HEATMAP_LAYER_SOURCE)
        layer.setProperties( // Color ramp for heatmap.  Domain is 0 (low) to 1 (high).
            // Begin color ramp at 0-stop with a 0-transparancy color
            // to create a blur-like effect.
            PropertyFactory.heatmapColor(
                Expression.interpolate(
                    Expression.linear(), Expression.heatmapDensity(),
                    Expression.literal(0), Expression.rgba(33, 102, 172, 0),
                    Expression.literal(0.2), Expression.rgb(103, 169, 207),
                    Expression.literal(0.4), Expression.rgb(209, 229, 240),
                    Expression.literal(0.6), Expression.rgb(253, 219, 199),
                    Expression.literal(0.8), Expression.rgb(239, 138, 98),
                    Expression.literal(1), Expression.rgb(178, 24, 43)
                )
            ), // Increase the heatmap weight based on frequency and property magnitude
            PropertyFactory.heatmapWeight(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("mag"),
                    Expression.stop(0, 0),
                    Expression.stop(6, 1)
                )
            ), // Increase the heatmap color weight weight by zoom level
            // heatmap-intensity is a multiplier on top of heatmap-weight
            PropertyFactory.heatmapIntensity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(0, 1),
                    Expression.stop(9, 3)
                )
            ), // Adjust the heatmap radius by zoom level
            PropertyFactory.heatmapRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(0, 2),
                    Expression.stop(9, 20)
                )
            ), // Transition from heatmap to circle layer by zoom level
            PropertyFactory.heatmapOpacity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(7, 1),
                    Expression.stop(9, 0)
                )
            )
        )
        return layer
    }

    private fun createCircleLayer(): CircleLayer {
        val circleLayer = CircleLayer(CIRCLE_LAYER_ID, EARTHQUAKE_SOURCE_ID)
        circleLayer.setProperties( // Size circle radius by earthquake magnitude and zoom level
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.literal(7),
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.get("mag"),
                        Expression.stop(1, 1),
                        Expression.stop(6, 4)
                    ),
                    Expression.literal(16),
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.get("mag"),
                        Expression.stop(1, 5),
                        Expression.stop(6, 50)
                    )
                )
            ), // Color circle by earthquake magnitude
            PropertyFactory.circleColor(
                Expression.interpolate(
                    Expression.linear(), Expression.get("mag"),
                    Expression.literal(1), Expression.rgba(33, 102, 172, 0),
                    Expression.literal(2), Expression.rgb(103, 169, 207),
                    Expression.literal(3), Expression.rgb(209, 229, 240),
                    Expression.literal(4), Expression.rgb(253, 219, 199),
                    Expression.literal(5), Expression.rgb(239, 138, 98),
                    Expression.literal(6), Expression.rgb(178, 24, 43)
                )
            ), // Transition from heatmap to circle layer by zoom level
            PropertyFactory.circleOpacity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(7, 0),
                    Expression.stop(8, 1)
                )
            ),
            PropertyFactory.circleStrokeColor("white"),
            PropertyFactory.circleStrokeWidth(1.0f)
        )
        return circleLayer
    }

    private fun createTestHeatSource(): GeoJsonSource {
        // Generate a wide, non-clustered FeatureCollection around Sulaimaniyah with varying weights (property "w")
        val json = generateHeatGeoJson(
            centerLat = CUSTOM_HEAT_LAT,
            centerLon = CUSTOM_HEAT_LON,
            pointCount = 6000,   // denser data
            latJitter = 0.05,    // ~5.5 km N/S
            lonJitter = 0.05,    // ~4.5 km E/W at this latitude
            minSpacingDeg = 0.0015 // ~150 m minimum spacing to avoid deep clustering
        )
        return GeoJsonSource(TEST_HEAT_SOURCE_ID, json)
    }

    private fun generateHeatGeoJson(
        centerLat: Double,
        centerLon: Double,
        pointCount: Int,
        latJitter: Double,
        lonJitter: Double,
        minSpacingDeg: Double
    ): String {
        val rnd = Random(42)

        // Simple Poisson-disk-like sampling using grid binning to enforce minimum spacing
        data class Pt(val lat: Double, val lon: Double, val w: Double)
        val cellSize = minSpacingDeg / 1.4142
        val grid = HashMap<Long, MutableList<Pt>>()
        val accepted = ArrayList<Pt>(pointCount)

        fun gridKey(lat: Double, lon: Double): Long {
            val gx = kotlin.math.floor((lon - (centerLon - lonJitter)) / cellSize).toLong()
            val gy = kotlin.math.floor((lat - (centerLat - latJitter)) / cellSize).toLong()
            return (gx shl 32) or (gy and 0xffffffffL)
        }

        fun neighbors(lat: Double, lon: Double): Sequence<Pt> = sequence {
            val gx = kotlin.math.floor((lon - (centerLon - lonJitter)) / cellSize).toLong()
            val gy = kotlin.math.floor((lat - (centerLat - latJitter)) / cellSize).toLong()
            for (dx in -2..2) for (dy in -2..2) {
                val key = ((gx + dx) shl 32) or ((gy + dy) and 0xffffffffL)
                grid[key]?.let { list ->
                    for (p in list) yield(p)
                }
            }
        }

        var attempts = 0
        val maxAttempts = pointCount * 20
        while (accepted.size < pointCount && attempts < maxAttempts) {
            attempts++
            val lat = centerLat + (rnd.nextDouble() * 2.0 - 1.0) * latJitter
            val lon = centerLon + (rnd.nextDouble() * 2.0 - 1.0) * lonJitter

            // enforce bounds
            if (lat < centerLat - latJitter || lat > centerLat + latJitter) continue
            if (lon < centerLon - lonJitter || lon > centerLon + lonJitter) continue

            var ok = true
            for (n in neighbors(lat, lon)) {
                val dlat = lat - n.lat
                val dlon = lon - n.lon
                val dist = kotlin.math.sqrt(dlat * dlat + dlon * dlon)
                if (dist < minSpacingDeg) { ok = false; break }
            }
            if (!ok) continue

            // Weight: more random, with a slight center bias
            val dx = (lon - centerLon) / lonJitter
            val dy = (lat - centerLat) / latJitter
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            var w = 1.0 - (dist / 1.8)
            if (w < 0.0) w = 0.0
            w = (w * 0.6) + rnd.nextDouble() * 0.4
            if (w > 1.0) w = 1.0

            val pt = Pt(lat, lon, w)
            accepted.add(pt)
            val key = gridKey(lat, lon)
            grid.getOrPut(key) { mutableListOf() }.add(pt)
        }

        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"type\":\"FeatureCollection\",\"features\":[")
        for (i in accepted.indices) {
            val p = accepted[i]
            if (i > 0) sb.append(',')
            sb.append('{')
            sb.append("\"type\":\"Feature\",\"properties\":{\"w\":")
            sb.append(String.format(Locale.US, "%.3f", p.w))
            sb.append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
            sb.append(String.format(Locale.US, "%.6f", p.lon))
            sb.append(',')
            sb.append(String.format(Locale.US, "%.6f", p.lat))
            sb.append("]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun createCustomHeatmapLayer(): HeatmapLayer {
        val layer = HeatmapLayer(TEST_HEAT_LAYER_ID, TEST_HEAT_SOURCE_ID)
        layer.maxZoom = 22f
        layer.setProperties(
            // Bold, distinct color ramp
            PropertyFactory.heatmapColor(
                Expression.interpolate(
                    Expression.linear(), Expression.heatmapDensity(),
                    Expression.literal(0.00), Expression.rgba(0, 0, 0, 0),
                    Expression.literal(0.10), Expression.rgba(0, 255, 255, 0.6),   // cyan
                    Expression.literal(0.40), Expression.rgba(0, 128, 0, 0.8),     // green
                    Expression.literal(0.70), Expression.rgba(255, 165, 0, 0.9),   // orange
                    Expression.literal(1.00), Expression.rgba(255, 0, 0, 1.0)      // red
                )
            ),
            // Weight from custom property "w"
            PropertyFactory.heatmapWeight(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("w"),
                    Expression.stop(0.0, 0.0),
                    Expression.stop(1.0, 1.0)
                )
            ),
            // Stronger intensity overall
            PropertyFactory.heatmapIntensity(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(0, 1.5),
                    Expression.stop(15, 4.0)
                )
            ),
            // Larger radius to exaggerate effect
            PropertyFactory.heatmapRadius(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(0, 6),
                    Expression.stop(13, 40),
                    Expression.stop(22, 60)
                )
            ),
            // Keep visible at high zoom
            PropertyFactory.heatmapOpacity(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(10, 1.0),
                    Expression.stop(22, 0.7)
                )
            )
        )
        return layer
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    public override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    // # --8<-- [start:constants]
    companion object {
        private const val EARTHQUAKE_SOURCE_URL =
            "https://maplibre.org/maplibre-gl-js/docs/assets/earthquakes.geojson"
        private const val EARTHQUAKE_SOURCE_ID = "earthquakes"
        private const val HEATMAP_LAYER_ID = "earthquakes-heat"
        private const val HEATMAP_LAYER_SOURCE = "earthquakes"
        private const val CIRCLE_LAYER_ID = "earthquakes-circle"

        // Custom heatmap IDs and location (Sulaimaniyah, Kurdistan Region, Iraq)
        private const val TEST_HEAT_SOURCE_ID = "test-heat-src"
        private const val TEST_HEAT_LAYER_ID = "test-heat-layer"
        private const val CUSTOM_HEAT_LAT = 35.5610
        private const val CUSTOM_HEAT_LON = 45.4330
    }
    // # --8<-- [end:constants]
}
