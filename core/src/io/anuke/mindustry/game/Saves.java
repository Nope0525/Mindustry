package io.anuke.mindustry.game;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.game.EventType.StateChangeEvent;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.io.SaveMeta;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.ThreadArray;

import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class Saves{
    private int nextSlot;
    private Array<SaveSlot> saves = new ThreadArray<>();
    private SaveSlot current;
    private boolean saving;
    private float time;

    public Saves(){
        Events.on(StateChangeEvent.class, (prev, state) -> {
            if(state == State.menu){
                threads.run(() -> current = null);
            }
        });
    }

    public void load(){
        saves.clear();
        for(int i = 0; i < saveSlots; i++){
            if(SaveIO.isSaveValid(i)){
                SaveSlot slot = new SaveSlot(i);
                saves.add(slot);
                slot.meta = SaveIO.getData(i);
                nextSlot = i + 1;
            }
        }
    }

    public SaveSlot getCurrent(){
        return current;
    }

    public void update(){
        SaveSlot current = this.current;

        if(!state.is(State.menu) && !state.gameOver && current != null && current.isAutosave()){
            time += Timers.delta();
            if(time > Settings.getInt("saveinterval") * 60){
                saving = true;

                Timers.run(2f, () -> {
                    try{
                        SaveIO.saveToSlot(current.index);
                        current.meta = SaveIO.getData(current.index);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                    saving = false;
                });

                time = 0;
            }
        }else{
            time = 0;
        }
    }

    public void resetSave(){
        current = null;
    }

    public boolean isSaving(){
        return saving;
    }

    public boolean canAddSave(){
        return nextSlot < saveSlots;
    }

    public void addSave(String name){
        SaveSlot slot = new SaveSlot(nextSlot);
        nextSlot++;
        slot.setName(name);
        saves.add(slot);
        SaveIO.saveToSlot(slot.index);
        slot.meta = SaveIO.getData(slot.index);
        current = slot;
    }

    public SaveSlot importSave(FileHandle file) throws IOException{
        SaveSlot slot = new SaveSlot(nextSlot);
        slot.importFile(file);
        nextSlot++;
        slot.setName(file.nameWithoutExtension());
        saves.add(slot);
        slot.meta = SaveIO.getData(slot.index);
        current = slot;
        return slot;
    }

    public Array<SaveSlot> getSaveSlots(){
        return saves;
    }

    public class SaveSlot{
        public final int index;
        SaveMeta meta;

        public SaveSlot(int index){
            this.index = index;
        }

        public void load(){
            SaveIO.loadFromSlot(index);
            meta = SaveIO.getData(index);
            current = this;
        }

        public void save(){
            SaveIO.saveToSlot(index);
            meta = SaveIO.getData(index);
            current = this;
        }

        public String getDate(){
            return meta.date;
        }

        public Map getMap(){
            return meta.map;
        }

        public String getName(){
            return Settings.getString("save-" + index + "-name", "untittled");
        }

        public void setName(String name){
            Settings.putString("save-" + index + "-name", name);
            Settings.save();
        }

        public int getBuild(){
            return meta.build;
        }

        public int getWave(){
            return meta.wave;
        }

        public Difficulty getDifficulty(){
            return meta.difficulty;
        }

        public GameMode getMode(){
            return meta.mode;
        }

        public boolean isAutosave(){
            return Settings.getBool("save-" + index + "-autosave", !gwt);
        }

        public void setAutosave(boolean save){
            Settings.putBool("save-" + index + "-autosave", save);
            Settings.save();
        }

        public void importFile(FileHandle file) throws IOException{
            try{
                file.copyTo(SaveIO.fileFor(index));
            }catch(Exception e){
                throw new IOException(e);
            }
        }

        public void exportFile(FileHandle file) throws IOException{
            try{
                if(!file.extension().equals(saveExtension)){
                    file = file.parent().child(file.nameWithoutExtension() + "." + saveExtension);
                }
                SaveIO.fileFor(index).copyTo(file);
            }catch(Exception e){
                throw new IOException(e);
            }
        }

        public void delete(){
            SaveIO.fileFor(index).delete();
            saves.removeValue(this, true);
            if(this == current){
                current = null;
            }
        }
    }
}
