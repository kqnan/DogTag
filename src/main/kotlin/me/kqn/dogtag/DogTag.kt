package me.kqn.dogtag

import com.sk89q.worldguard.bukkit.RegionContainer
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.BooleanFlag
import me.kqn.dogtag.file.ConfigObject
import me.kqn.dogtag.file.ConfigObject.conf
import me.kqn.dogtag.file.MessageObject.message
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getPluginManager
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.io.newFile
import taboolib.common.platform.Plugin
import taboolib.common.platform.command.command
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import taboolib.common5.Baffle
import taboolib.common5.FileWatcher
import taboolib.expansion.*
import taboolib.library.xseries.getItemStack
import taboolib.module.chat.colored
import taboolib.module.configuration.Configuration
import taboolib.platform.util.giveItem
import taboolib.platform.util.isRightClick
import taboolib.platform.util.killer
import taboolib.platform.util.onlinePlayers
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

object  DogTag : Plugin() {


    lateinit var dogtag:ItemStack
    lateinit var baffle: Baffle
    lateinit var baffle2: Baffle
     var confPath="plugins/DogTag/config.yml"
    override fun onEnable() {
        baffle2=Baffle.of(500.toLong(),TimeUnit.MILLISECONDS)
        baffle=Baffle.of(conf.getInt("CoolDown",10).toLong(),TimeUnit.SECONDS)
        if (conf.getBoolean("database.enable")) {
            setupPlayerDatabase(conf.getConfigurationSection("database")!!)
        } else {
            setupPlayerDatabase(newFile(getDataFolder(), "data.db"))
        }
         dogtag= conf.getItemStack("Item")?: ItemStack(Material.NAME_TAG)
        FileWatcher.INSTANCE.addSimpleListener(File(confPath)){
            reload()
            onlinePlayers.forEach { if (it.isOp){
            it.sendMessage("&a���Զ�����".colored())
            }
            }
        }
        regCmd()
    }

    override fun onDisable() {
        baffle.resetAll()
        baffle2.resetAll()
    }
    private var worldGuardPlugin: WorldGuardPlugin? = null
    private var regionContainer: RegionContainer? = null
    override fun onLoad() {

        this.worldGuardPlugin = getPluginManager().getPlugin("WorldGuard") as WorldGuardPlugin
        val booleanFlag: BooleanFlag = Flag.SAFE_AREA
        this.regionContainer = worldGuardPlugin!!.regionContainer
        try {
            if (worldGuardPlugin!!.flagRegistry.get("DogTag-Safe-Area") != null) return
            worldGuardPlugin!!.flagRegistry.register(booleanFlag)
        } catch (e: Exception) {
            e.printStackTrace()
            Bukkit.getLogger().warning("[DogTag]δ����WorldGuard,��ȫ�����ܽ�������")
        }
    }
    fun  reload(){
        conf= Configuration.loadFromFile(File(confPath))
        dogtag= conf.getItemStack("Item")?: ItemStack(Material.NAME_TAG)
        baffle.resetAll()

        baffle=Baffle.of(conf.getInt("CoolDown",10).toLong(),TimeUnit.SECONDS)
    }

    fun regCmd(){
        command("dogtag"){
            createHelper()
            literal("reload"){
                execute<CommandSender>{
                    sender, context, argument ->
                    submitAsync { reload()
                    sender.sendMessage("&a���")
                    }
                }
            }
            literal("get"){
                dynamic ("����"){
                    execute<Player>{
                        sender, context, argument ->
                        if(argument.toIntOrNull()!=null){
                            sender.giveItem(dogtag.clone().apply { amount=argument.toInt() })
                        }
                        else {
                            sender.sendMessage("&a��������ȷ��������".colored())
                        }

                    }
                }
            }
            literal("remove"){
                dynamic ("����"){
                    dynamic("�����") {
                        execute<Player>(){
                            sender, context, argument ->
                            val amt=context.argument(-1).toIntOrNull()?:return@execute
                            val player=Bukkit.getPlayer(context.argument(0))?:return@execute
                            debug(amt.toString()+"  "+player.name)
                            var points=player.getDataContainer()[pointKey]?.let { it.toInt() }?:return@execute
                            //var level=player.getDataContainer()[levelKey]?.let{it.toInt()}?:return@execute
                            var newPoints= max(points-amt,0)
                            var newLevel=0;
                            var levels= conf.getConfigurationSection("Levels")!!.getKeys(false).sortedWith(){x,y->
                                return@sortedWith (x.toIntOrNull()?:0)-(y.toIntOrNull()?:0)
                            }
                            for (key in levels) {
                                if(conf.getInt("Levels.${key}.exp",0)<=newPoints){
                                    newLevel=key.toInt()

                                }
                                else {
                                    break
                                }
                            }
                            player.getDataContainer()[pointKey]=newPoints
                            player.getDataContainer()[levelKey]=newLevel
                            sender.sendMessage("&a���Ƴ�${player.name}��${amt}����������")
                        }
                    }
                }
            }
            literal("points"){
                dynamic ("�����"){
                    execute<CommandSender>{
                        sender, context, argument ->
                        var p=Bukkit.getPlayer(argument)
                        if(p==null){
                            sender.sendMessage("����Ҳ�����")
                        }
                        else {
                            sender.sendMessage("���${argument}����������Ϊ${p.getDataContainer()[pointKey]?:0}")
                            sender.sendMessage("���${argument}�������ȼ�Ϊ${p.getDataContainer()[levelKey]?:0}")
                        }
                    }

                }
            }


        }
    }

    @SubscribeEvent
    fun playerDeath(e:PlayerDeathEvent){
        debug("name: ${e.entity.name}  uuid: ${e.entity.uniqueId} killer: ${e.killer?.name} killer: ${e.killer?.uniqueId}")
        if(e.entity.killer!=null&&e.entity.killer!!.name!=e.entity.name&&e.entity.uniqueId!=e.entity.uniqueId){
            debug("${worldGuardPlugin!=null}")
            if (worldGuardPlugin != null) {
                var player=e.entity
                val localPlayer = worldGuardPlugin!!.wrapPlayer(player)
                debug(player.location.toString())
                val regions = regionContainer!!.createQuery().getApplicableRegions(player.getLocation())

                val keepInventory = regions.queryValue(localPlayer, Flag.SAFE_AREA)
                debug(keepInventory?.toString()?:"")
                if (keepInventory != null && keepInventory==true) {
                    return
                }
            }
            debug(baffle.hasNext(e.entity.uniqueId.toString()).toString())
            if(baffle.hasNext(e.entity.uniqueId.toString())){
                baffle.next(e.entity.uniqueId.toString())
                debug(e.entity.world.name+"  "+e.entity.location.toString())

                e.entity.world.dropItemNaturally(e.entity.location, dogtag.also { it.amount=1 })
            }
        }
    }
    val pointKey="dogtag_points"
    val levelKey="dogtag_levels"
    //�Ҽ����ƻ�ȡ����
    @SubscribeEvent
    fun onClick(e:PlayerInteractEvent){
        if(e.isRightClick()){
            var mainhand=e.player.inventory.itemInMainHand

            if(dogtag.apply { amount=mainhand.amount } == mainhand){
                if(!baffle2.hasNext(e.player.name)){
                    return
                }
                baffle2.next(e.player.name)
                mainhand.amount=mainhand.amount-1
                var points= conf.getInt("Points")
                var p= e.player.getDataContainer()[pointKey]
                if(p==null){
                    e.player.getDataContainer()[pointKey] = points//û�������
                }
                else {
                    e.player.getDataContainer()[pointKey] = p.toInt()+points//�������
                }
                e.player.sendMessage(message.getString("GET_POINTS","")!!.replace("%amount%",points.toString()).colored())//������Ϣ���滻ռλ��
                //�����ж�����
                var total= e.player.getDataContainer()[pointKey]!!.toInt()
                var levels= conf.getConfigurationSection("Levels")!!.getKeys(false).sortedWith(){x,y->
                    return@sortedWith (x.toIntOrNull()?:0)-(y.toIntOrNull()?:0)
                }
                var lvlNow=e.player.getDataContainer()[levelKey]?.toInt()?:0//ȡ���ڵĵȼ�
                debug("level: ${levels}")
                for (it in levels) {
                    var need= conf["Levels.${it}.exp"] as Int

                    if(lvlNow<it.toInt()) {
                        if (need <= total) {
                            e.player.sendMessage(
                                message.getString("UPGRADE")!!.replace("%level%", it).colored()
                            )//����������Ϣ
                            e.player.getDataContainer()[levelKey] = it//�ѵȼ��������ݿ�
                            var cmds= conf.getStringList("Levels.${it}.command")
                            for (cmd in cmds) {
                                var tmp=e.player.isOp
                                e.player.isOp=true
                                e.player.performCommand(cmd.replace("[player]",e.player.name))//�����ִ������
                                e.player.isOp=tmp
                            }
                        }
                        else {
                            break
                        }
                    }
                }


            }
        }
    }
    @SubscribeEvent
    fun setupData(e: PlayerJoinEvent) {
        // ��ʼ���������

        e.player.setupDataContainer()

    }
    @SubscribeEvent
    fun releaseDAta(e: PlayerQuitEvent) {
        // �ͷ������������
        e.player.releaseDataContainer()
    }
}