# Natural behavior in the pixel sky

Reviewed 22 October 2026. These rules guide the artwork; the shader is a stylized illustration, not a fluid, optical or astronomical simulation. All current weather values remain clearly labelled sample data.

## Precipitation and cloud depth

Water droplets grow inside clouds and fall when sufficiently large, as described by the [Met Office rain guide](https://weather.metoffice.gov.uk/learn-about/weather/types-of-weather/rain). The artwork therefore attaches the rain curtain to moving cloud bases. It evaluates the lower envelope of all cloud lobes, shifts the rain's source coordinate with its lateral drift, and masks rain from cloud faces. The visible curtain is also restricted to the current horizontal cloud footprint so a displaced sample cannot create a detached edge fringe as a cloud exits. The same coverage mask keeps snow in front of open sky below the clouds. This fixes the previous fixed-height curtain and the appearance of rain painted over cloud surfaces. Not every visible cloud is treated as an independently raining volume.

## Light, shadow and reflection

Sunbeams depend on cloud gaps and scattering in the air; see the [WMO International Cloud Atlas](https://cloudatlas.wmo.int/crepuscular-rays.html). The shader shares an atmospheric transmission envelope across the image and estimates the solar obstruction by the moving cloud layers. That obstruction also controls shaft brightness. A small analytic volume-light contribution follows the light origin, while most of the cloud appearance retains the established clean gradient and silhouette. This is not a full three-dimensional shadow solver.

Dawn and sunset move the solar disc slowly in opposite vertical directions. The sea reflection follows its horizontal position. Sunlight, cloud shading, sky tone and reflections consequently change together rather than behaving as unrelated flashing sprites. The common transmission variation is an art-directed approximation, not an additional Open-Meteo measurement.

## Fog and visibility

[NOAA](https://www.noaa.gov/jetstream/synoptic/types-of-weather-phenomena) describes fog as suspended droplets that restrict visibility; the [NWS advection-fog guide](https://www.weather.gov/safety/fog-advection) explains how low-level wind transports fog over the landscape. The illustration uses a softened distant ridge, a nearer island and lighthouse, and moving translucent veils that obscure and reveal them. A shared perspective water plane, low wet shoreline, fragmented near-shore ripples and tower contact shadows connect the landmark to the sea. These are composition cues, not a surveyed coast. A warm sweeping beacon makes the drifting mist more legible; its beam and timing are artistic choices rather than measured optical behavior. Fine mode blends density through small ordered-dither steps rather than switching large white pixel bands on and off. The coast and lighthouse are illustrative, not a map or landmark of the selected city.

## Stars and the Moon

[NASA StarChild](https://starchild.gsfc.nasa.gov/docs/StarChild/questions/question26.html) attributes stellar twinkling to turbulent air bending the incoming light. Stars therefore vary independently across three depth/brightness fields, rather than blinking as one layer. [NASA's Moonlight guide](https://science.nasa.gov/moon/moonlight/) explains that the Moon reflects sunlight. The visible lunar surface remains comparatively stable, with subdued maria/crater marks and slow atmospheric-halo variation. Its phase and surface marks are decorative; no live lunar-phase or star-catalogue data is connected.

## Long-form motion

The GPU renderer has no short whole-scene loop. Clouds travel in one direction and wrap beyond the visible frame. Light, water, wind and precipitation use independent rates; lightning, meteors and bird passages vary in successive scheduling windows. Those window sizes are composition choices, not scientific recurrence periods. Pause freezes elapsed time and resume continues from that time. The simpler Canvas fallback retains its earlier eight-second loop.
