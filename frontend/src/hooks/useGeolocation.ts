import { useCallback, useState } from 'react';

interface Coords {
  lat: number;
  lng: number;
}

export function useGeolocation() {
  const [coords, setCoords] = useState<Coords | null>(null);
  const [status, setStatus] = useState<'idle' | 'locating' | 'granted' | 'denied' | 'unsupported'>('idle');

  const request = useCallback(() => {
    if (!('geolocation' in navigator)) {
      setStatus('unsupported');
      return;
    }
    setStatus('locating');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setStatus('granted');
      },
      () => setStatus('denied'),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 }
    );
  }, []);

  return { coords, status, request };
}
