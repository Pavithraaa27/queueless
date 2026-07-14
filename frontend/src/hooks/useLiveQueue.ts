import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { Ticket } from '../types';
import { API_BASE_URL } from '../api/client';
import { getSnapshot } from '../api/queue';

/**
 * Subscribes to /topic/queue/{businessId} for live queue snapshots.
 * Falls back to the REST snapshot endpoint on mount, then STOMP keeps it fresh.
 */
export function useLiveQueue(businessId: number | null) {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!businessId) return;

    getSnapshot(businessId).then(setTickets).catch(() => {});

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/queue/${businessId}`, (message) => {
          try {
            const payload = JSON.parse(message.body) as Ticket[];
            setTickets(payload);
          } catch {
            // ignore malformed frame
          }
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [businessId]);

  return { tickets, connected };
}
