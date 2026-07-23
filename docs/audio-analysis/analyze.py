import json, essentia
essentia.log.warningActive=False; essentia.log.infoActive=False
import essentia.standard as es

TRACKS=[
 ("Ikeda — data.reflex",   '/Volumes/music/Ryoji Ikeda/Dataplex/15 - data.reflex.mp3'),
 ("Ikeda — data.matrix",   '/Volumes/music/Ryoji Ikeda/Dataplex/19 - data.matrix.mp3'),
 ("Bretschneider — Soft Throbbing", '/Volumes/music/Frank Bretschneider/Rhythm/01 - A Soft Throbbing of Time.mp3'),
 ("Bretschneider — Construction Shack", '/Volumes/music/Frank Bretschneider/Rhythm/06 - Construction Shack.mp3'),
 ("Bretschneider — Looping VI", '/Volumes/music/Frank Bretschneider/Looping I-VI (And Other Assorted Love Songs)/12 - Looping VI.flac'),
 ("Kangding Ray — Downshifters", '/Volumes/music/Kangding Ray/Automne Fold/03 - Downshifters.mp3'),
 ("SND — 1", '/Volumes/music/SND/Tender Love/01 - 1.flac'),
]
def g(f,k,d=None):
    try: return f[k]
    except: return d
rows=[]
for name,path in TRACKS:
    try:
        feat,_=es.MusicExtractor(lowlevelStats=['mean','stdev'],
            rhythmStats=['mean','stdev'], tonalStats=['mean','stdev'])(path)
    except Exception as e:
        print("FAIL",name,e); continue
    eb=[g(feat,'lowlevel.spectral_energyband_low.mean',0),
        g(feat,'lowlevel.spectral_energyband_middle_low.mean',0),
        g(feat,'lowlevel.spectral_energyband_middle_high.mean',0),
        g(feat,'lowlevel.spectral_energyband_high.mean',0)]
    s=sum(eb) or 1
    ebr=[round(100*x/s,1) for x in eb]
    r=dict(
      name=name,
      dur=round(g(feat,'metadata.audio_properties.length',0),1),
      sr=g(feat,'metadata.audio_properties.sample_rate',0),
      ch=g(feat,'metadata.audio_properties.number_channels',0),
      lossless=g(feat,'metadata.audio_properties.lossless',0),
      bpm=round(g(feat,'rhythm.bpm',0),1),
      bpm2=round(g(feat,'rhythm.bpm_histogram_second_peak_bpm',0),1),
      onset_rate=round(g(feat,'rhythm.onset_rate',0),2),
      danceability=round(g(feat,'rhythm.danceability',0),2),
      beats=int(g(feat,'rhythm.beats_count',0)),
      LUFS=round(g(feat,'lowlevel.loudness_ebu128.integrated',0),1),
      LRA=round(g(feat,'lowlevel.loudness_ebu128.loudness_range',0),1),
      dyn_complexity=round(g(feat,'lowlevel.dynamic_complexity',0),2),
      centroid=round(g(feat,'lowlevel.spectral_centroid.mean',0)),
      centroid_sd=round(g(feat,'lowlevel.spectral_centroid.stdev',0)),
      rolloff=round(g(feat,'lowlevel.spectral_rolloff.mean',0)),
      flatness_db=round(g(feat,'lowlevel.melbands_flatness_db.mean',0),3),
      entropy=round(g(feat,'lowlevel.spectral_entropy.mean',0),2),
      zcr=round(g(feat,'lowlevel.zerocrossingrate.mean',0),4),
      dissonance=round(g(feat,'lowlevel.dissonance.mean',0),3),
      pitch_salience=round(g(feat,'lowlevel.pitch_salience.mean',0),3),
      spec_complexity=round(g(feat,'lowlevel.spectral_complexity.mean',0),1),
      hfc=round(g(feat,'lowlevel.hfc.mean',0),1),
      strongpeak=round(g(feat,'lowlevel.spectral_strongpeak.mean',0),3),
      silence_rate_30=round(g(feat,'lowlevel.silence_rate_30dB.mean',0),3),
      spec_spread=round(g(feat,'lowlevel.spectral_spread.mean',0)),
      band_low_pct=ebr[0], band_midlow_pct=ebr[1], band_midhigh_pct=ebr[2], band_high_pct=ebr[3],
      key=f"{g(feat,'tonal.key_edma.key','?')} {g(feat,'tonal.key_edma.scale','?')}",
      key_strength=round(g(feat,'tonal.key_edma.strength',0),2),
      chords_changes=round(g(feat,'tonal.chords_changes_rate',0),3),
    )
    rows.append(r)
    print(f"done: {name}")
json.dump(rows, open(f"{__import__('os').path.dirname(__file__)}/analysis.json","w"), indent=1)
print("WROTE analysis.json with",len(rows),"tracks")
