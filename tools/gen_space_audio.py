"""Original synthesized orbital soundscape. Requires numpy + soundfile (Ogg/Vorbis); no samples or recordings."""
from pathlib import Path
import numpy as np
import soundfile as sf

SR = 44100
OUT = Path('src/main/resources/assets/dbzenith/sounds')
rng = np.random.default_rng(141)

def noise(n, cutoff):
    spectrum = np.fft.rfft(rng.standard_normal(n))
    spectrum *= np.exp(-(np.fft.rfftfreq(n, 1/SR)/cutoff)**4)
    v = np.fft.irfft(spectrum, n)
    return v / max(1e-6, np.std(v))

def save(name, v):
    # Soft crossfade at loop seam, then a conservative peak ceiling.
    edge = min(SR//4, len(v)//8)
    ramp = np.linspace(0, 1, edge)
    v[:edge] *= ramp
    v[-edge:] *= ramp[::-1]
    v = np.tanh(v)*.6
    OUT.mkdir(parents=True, exist_ok=True)
    with sf.SoundFile(OUT/(name+'.ogg'), 'w', samplerate=SR, channels=1, format='OGG', subtype='VORBIS') as output:
        for begin in range(0, len(v), 4096):
            output.write(v[begin:begin+4096].astype(np.float32))
    print(name)

t = np.arange(SR*16)/SR
drone = .13*np.sin(2*np.pi*37.5*t)+.065*np.sin(2*np.pi*56.25*t)+.025*noise(len(t),170)
drone *= .65+.2*np.sin(2*np.pi*t/16)
save('space_drone', drone)
t = np.arange(SR*8)/SR
cycle = np.mod(t,4)
inhale = np.sin(np.pi*np.clip(cycle/1.6,0,1))**2
exhale = np.sin(np.pi*np.clip((cycle-1.9)/1.9,0,1))**2
save('space_breath', noise(len(t),1200)*(.065*inhale+.085*exhale)+.009*np.sin(2*np.pi*92*t)*exhale)
t = np.arange(SR*3)/SR
beat = np.zeros_like(t)
for start in (0,.19,1.5,1.69):
    u=np.maximum(0,t-start)
    beat += np.where(t>=start, np.sin(2*np.pi*(57*u-14*u*u))*np.exp(-u*25)*.32,0)
save('space_heartbeat', beat)
t = np.arange(SR*8)/SR
save('space_entry', noise(len(t),1700)*(.18+.06*np.sin(2*np.pi*t/8))+.09*np.sin(2*np.pi*48*t))
t = np.arange(SR*32)/SR
music = np.zeros_like(t)
for i,f in enumerate((55,82.4069,110,146.8324,164.8138)):
    envelope=(.5+.5*np.sin(2*np.pi*t/32+i*.8))**2
    music += .028*np.sin(2*np.pi*f*t+np.sin(2*np.pi*t/16)*.2)*envelope
save('space_music', music)
